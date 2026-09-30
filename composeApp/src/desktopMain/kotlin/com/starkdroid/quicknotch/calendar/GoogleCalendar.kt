package com.starkdroid.quicknotch.calendar

import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.datasource.AppLog
import com.starkdroid.quicknotch.datasource.AppPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

sealed interface CalendarState {
    /** No tokens on disk: the user has never connected. */
    data object Disconnected : CalendarState

    /** A browser round-trip is in flight (waiting for the consent screen). */
    data object Connecting : CalendarState

    /**
     * Connected. [events] is the most recent successful fetch — or the cached
     * list at launch, until the first fetch lands. [staleError] marks a failed
     * refresh that left a still-good list on screen.
     */
    data class Connected(
        val account: String,
        val events: List<CalendarEvent>,
        val fetchedAtMillis: Long,
        val staleError: String? = null
    ) : CalendarState {
        /** The timed event to put on the ticker at [nowMillis], if any. */
        fun tickerEventAt(nowMillis: Long): CalendarEvent? = events.firstOrNull { it.isTickerLiveAt(nowMillis) }
    }

    /**
     * Something failed. [reason] is a stable key the UI localises (never a raw
     * server message, which would leak internals into the settings window).
     */
    data class Error(val reason: String) : CalendarState
}

/**
 * Google Calendar, read-only, via the OAuth 2.0 authorization-code flow with
 * PKCE and a loopback redirect — the shape Google documents for installed
 * desktop apps, where no client secret exists and PKCE is what protects the
 * code exchange.
 *
 * Everything runs in the poller's own IO scope: the consent round-trip blocks
 * on a loopback socket, so it can never happen on the EDT.
 *
 * - Tokens live in `calendar.json` beside the config, and the last good event
 *   list in `calendar-events.json`, so a cold start shows today's plan before
 *   any network call completes.
 * - The access token is refreshed on demand (expiry skew, or a 401 mid-flight)
 *   and a revoked refresh token drops straight back to [CalendarState.Disconnected]
 *   so the widget offers "Connect" instead of looping failures.
 * - A failed refresh never clears a good list; it annotates it with
 *   [CalendarState.Connected.staleError].
 */
object GoogleCalendar {
    // Desktop-app client id from Google Cloud (APIs & Services → Credentials →
    // OAuth client ID → Desktop app). Desktop clients are public by design:
    // PKCE protects the exchange, so there is no secret to protect.
    private const val CLIENT_ID = "317663226214-ojbngp1n0p81tttll9sjq9ou74mvjegk.apps.googleusercontent.com"

    /**
     * Google's token endpoint demands a client secret from every client type,
     * Desktop included (see [CalendarApi.exchangeCode] for the evidence). It is
     * extractable from any shipped binary, which is inherent to desktop OAuth —
     * the same reason gcloud ships its own. It authorizes nothing alone: every
     * token still needs a real user's interactive consent. Rotate it in the
     * Cloud console if it ever leaks wider than this repo.
     */
    private const val CLIENT_SECRET = "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"

    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
    private const val EVENTS_ENDPOINT = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

    /** Read-only calendar, plus the address used to label the connection. */
    private const val SCOPE =
        "https://www.googleapis.com/auth/calendar.readonly " +
            "https://www.googleapis.com/auth/userinfo.email"

    private const val POLL_MS = 30 * 60 * 1000L
    private const val TOKEN_SKEW_MS = 60_000L
    private const val CONNECT_TIMEOUT_MS = 5 * 60 * 1000L

    /**
     * Per-stage tracing for the consent handshake, the one flow that is opaque
     * from the outside (a browser is involved, and a failure used to look the
     * same as every other failure). On by default: it only ever prints while
     * the user is deliberately connecting, and it is the difference between
     * "it did not work" and a fixable sentence.
     */
    private const val VERBOSE = true

    /** Reason keys; the UI maps them to localized strings. */
    const val REASON_AUTH = "auth"
    const val REASON_NETWORK = "network"
    const val REASON_REVOKED = "revoked"
    /** The user closed the consent screen or declined the app. */
    const val REASON_CANCELLED = "cancelled"
    /**
     * Google refused because the OAuth consent screen is still in Testing and
     * the signed-in account is not one of its test users. Arrives as
     * `access_denied` like a plain decline, so it has to be told apart by the
     * description or the user is told they cancelled something they did not.
     */
    const val REASON_NOT_VERIFIED = "not_verified"
    /**
     * The OAuth client is registered as a Web application, so Google treats it
     * as confidential and wants a client_secret. A desktop client is a *public*
     * client: PKCE is the authentication and there is no secret to send — the
     * remedy is the client type in the Cloud console, never a secret in the
     * binary. Distinct from a generic refusal because the consent screen worked
     * and only the exchange was rejected, which makes it baffling otherwise.
     */
    const val REASON_CLIENT_TYPE = "client_type"

    /**
     * All four Google calls, owned by the common (multiplatform) layer. One
     * client for the process: it owns a connection pool and a selector thread,
     * and a client per request would throw both away.
     */
    private val api = CalendarApi(CLIENT_ID, CLIENT_SECRET)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val random = SecureRandom()

    private val _state = MutableStateFlow<CalendarState>(CalendarState.Disconnected)
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    /**
     * The event whose ticker is up right now, or null. Resolved here rather than
     * in the overlay so "is an event live" is one rule next to the window that
     * defines it — the notch only has to read a flow.
     */
    private val _ticker = MutableStateFlow<CalendarEvent?>(null)
    val ticker: StateFlow<CalendarEvent?> = _ticker.asStateFlow()

    private val tokenFile: File get() = File(AppPaths.configDir, "calendar.json")
    private val eventsFile: File get() = File(AppPaths.configDir, "calendar-events.json")

    private var cached: Tokens? = null

    /**
     * Why the last [connect] attempt gave up, as one of the REASON_ keys. A
     * field rather than a return value because [authorize] is where the detail
     * lives and it already returns null for every outcome.
     */
    private var connectFailure: String = REASON_AUTH
    private var connectJob: Job? = null
    private var pendingCallback: CompletableFuture<String>? = null
    private var cancelledByUser = false
    private var tickerJob: Job? = null
    private var pollJob: Job? = null

    // ── lifecycle ───────────────────────────────────────────────────────────

    /** Start the poller. Idempotent; called once from the app. */
    fun start() {
        if (pollJob != null) return

        // The ticker loop is armed *before* the token check on purpose: it
        // resolves against whatever is connected right now, and connecting is
        // something the user does from settings long after launch. Started only
        // on the "already connected at launch" path it would never run for that
        // user — they would get the day and the badge, and a permanently empty
        // ticker. It is cheap when idle: one delay, one equality check.
        startTicker()

        cached = readTokens()
        if (cached == null) {
            _state.value = CalendarState.Disconnected
            return
        }
        // Show the cached day immediately, then refresh in the background.
        _state.value = CalendarState.Connected(
            account = cached!!.account,
            events = readCachedEvents(),
            fetchedAtMillis = 0L
        )
        pollJob = scope.launch {
            refreshBlocking()
            while (isActive) {
                delay(POLL_MS)
                refreshBlocking()
            }
        }
    }

    /**
     * A cheap 1s tick that only writes when the answer changes: event boundaries
     * are all the ticker cares about, and an unchanged value costs no
     * recomposition (pitfall 29's rule, applied to a flow rather than an
     * animation). 1s is finer than the resolution any calendar UI needs.
     */
    private fun startTicker() {
        if (tickerJob != null) return
        tickerJob = scope.launch {
            while (isActive) {
                val next = (_state.value as? CalendarState.Connected)
                    ?.tickerEventAt(System.currentTimeMillis())
                if (next != _ticker.value) _ticker.value = next
                delay(1000L)
            }
        }
    }

    /** Open the consent screen and wait for the code to come back on loopback. */
    fun connect() {
        if (_state.value is CalendarState.Connecting) return
        cancelledByUser = false
        // Each attempt starts from the generic reason. Without this the widget
        // reported whatever the *previous* failure was whenever a path returned
        // null without setting one (a rejected token exchange, say), so a
        // "not verified" from two clicks ago was still on screen after an
        // unrelated one.
        connectFailure = REASON_AUTH
        connectJob = scope.launch {
            _state.value = CalendarState.Connecting
            val tokens = authorize()
            if (tokens == null) {
                // Walking away from the consent screen is not a failure, so it
                // lands back on Disconnected rather than an error the user has
                // to think about.
                _state.value =
                    if (cancelledByUser) CalendarState.Disconnected
                    else CalendarState.Error(connectFailure)
                return@launch
            }
            cached = tokens
            writeTokens(tokens)
            log("token exchange succeeded")
            // Connected is true the moment tokens exist; whether the first day
            // fetch works is a separate state that layers on top. Landing here
            // first is what keeps a slow or failing first fetch from reading as
            // "sign-in didn't finish" after a sign-in that fully succeeded --
            // which is exactly what three console theories failed to explain.
            _state.value = CalendarState.Connected(
                account = tokens.account.ifBlank { "Google" },
                events = emptyList(),
                fetchedAtMillis = 0L
            )
            // Consent succeeding proves nothing; the first fetch is the real test.
            refreshBlocking()
        }
    }

    /**
     * Give up on a sign-in in progress. Without this the widget sat on a spinner
     * for the full five-minute budget whenever the browser never showed up —
     * which is indistinguishable, from the settings window, from a hang.
     */
    fun cancelConnect() {
        if (_state.value !is CalendarState.Connecting) return
        cancelledByUser = true
        log("cancelled by the user")
        pendingCallback?.completeExceptionally(CancellationException("cancelled by the user"))
        connectJob?.cancel()
        _state.value = CalendarState.Disconnected
    }

    /** Forget the tokens and stop showing anything. */
    fun disconnect() {
        cached = null
        pollJob?.cancel()
        pollJob = null
        runCatching { tokenFile.delete() }
        runCatching { eventsFile.delete() }
        _state.value = CalendarState.Disconnected
        // Otherwise the pill keeps scrolling an event from a calendar we just
        // unhooked; the loop itself keeps running and resolves null.
        _ticker.value = null
    }

    /** The settings widget's "Refresh now". */
    fun refresh() {
        scope.launch { refreshBlocking() }
    }

    // ── OAuth ───────────────────────────────────────────────────────────────

    /**
     * The whole consent handshake, in one try so that *nothing* can leave the
     * widget spinning: a throw while setting up the listener used to escape
     * uncaught, kill the coroutine, and strand the state on [CalendarState.Connecting]
     * with no log line and no way out.
     */
    private suspend fun authorize(): Tokens? {
        val verifier = randomUrlToken(64)
        val challenge = base64Url(sha256(verifier))
        val started = System.currentTimeMillis()
        val received = CompletableFuture<String>()
        var listener: ServerSocket? = null
        var listenerThread: Thread? = null
        var redirect = ""
        return try {
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            server.soTimeout = CONNECT_TIMEOUT_MS.toInt()
            listener = server
            redirect = "http://127.0.0.1:${server.localPort}/"

            // One request, one answer, then done: the redirect is a single GET
            // carrying Google's answer in the query string.
            listenerThread = Thread({
                runCatching { serveCallback(server) }
                    .onFailure { received.completeExceptionally(it) }
            }, "quicknotch-oauth-callback").apply {
                isDaemon = true
                start()
            }
            pendingCallback = received
            log("consent server on $redirect")

            AppLauncher.browse(
                AUTH_ENDPOINT + "?" + listOf(
                    "client_id" to CLIENT_ID,
                    "redirect_uri" to redirect,
                    "response_type" to "code",
                    "scope" to SCOPE,
                    // Offline so we get a refresh token at all. `prompt=consent`
                    // only on a fresh connect: without it Google silently
                    // withholds the refresh token for an already-authorised app.
                    "access_type" to "offline",
                    "prompt" to "consent",
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256"
                ).joinToString("&") { (k, v) -> "$k=" + urlEncode(v) }
            )
            log("browser handed off, waiting for the callback (${CONNECT_TIMEOUT_MS / 60_000} min budget)")

            val code = received.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            log("callback carried a code after ${(System.currentTimeMillis() - started) / 1000}s")
            exchangeCode(code, redirect, verifier)
        } catch (e: CancellationException) {
            // The user walking away is not an error: rethrow so connect() can
            // put the widget back to Disconnected instead of Error.
            throw e
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is GoogleAuthException) {
                // access_denied is the user closing the tab or picking "Cancel";
                // anything else is Google's refusal and needs a real reason shown.
                connectFailure = classifyConsentRefusal(cause)
                log("consent refused: ${cause.error} ${cause.description.orEmpty()}".trim())
                AppLog.line("Google consent failed (${cause.error}): ${cause.description.orEmpty()}")
            } else {
                connectFailure = REASON_AUTH
                log("consent wait failed: $cause")
                AppLog.line("Google sign-in wait failed: $cause")
            }
            null
        } catch (e: TimeoutException) {
            connectFailure = REASON_AUTH
            log("no callback after ${CONNECT_TIMEOUT_MS / 60_000} min")
            AppLog.line("Google sign-in got no callback within ${CONNECT_TIMEOUT_MS / 60_000} min")
            null
        } catch (e: Exception) {
            connectFailure = REASON_AUTH
            log("consent failed: $e")
            AppLog.line("Google sign-in failed: $e")
            null
        } catch (e: Error) {
            // NoClassDefFoundError and friends are Errors, not Exceptions, so
            // the clause above misses them entirely — which is exactly how a
            // missing runtime module used to vanish without a trace.
            connectFailure = REASON_AUTH
            log("consent died: $e")
            AppLog.line("Google sign-in died: $e")
            null
        } finally {
            pendingCallback = null
            runCatching { listener?.close() }
            listenerThread?.interrupt()
        }
    }

    /**
     * Answer one loopback request and pull the answer out of its query string.
     *
     * Hand-rolled on a plain [ServerSocket] rather than
     * `com.sun.net.httpserver.HttpServer`: jlink trims the runtime jpackage
     * bundles, and `jdk.httpserver` is not in the image Compose ships, so
     * referencing that class threw `NoClassDefFoundError` in the packaged app
     * and nowhere else. `ServerSocket` is `java.base`, so it is always there.
     * The whole exchange is four headers and a small page, which is all this
     * needs to be.
     */
    private fun serveCallback(server: ServerSocket) {
        server.accept().use { socket ->
            socket.soTimeout = 15_000
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            val target = requestLine.split(" ").getOrNull(1).orEmpty()
            // Read the headers off so the browser never writes into a full
            // buffer while we are composing the reply.
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val body = CONSENT_PAGE.toByteArray()
            val head = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray()
            socket.getOutputStream().apply {
                write(head)
                write(body)
                flush()
            }

            val query = queryParams(target.substringAfter('?', ""))
            when {
                // Google bounces the browser back with `error=...` when consent
                // was declined, the client is unknown, or the grant is blocked.
                // Read it instead of sitting out the whole timeout on a callback
                // that will never carry a code — that wait is what made a
                // declined sign-in and a broken one look identical.
                query["error"] != null -> {
                    log("callback carried ${query.getValue("error")}")
                    pendingCallback?.completeExceptionally(
                        GoogleAuthException(query.getValue("error"), query["error_description"])
                    )
                }
                query["code"] != null -> {
                    log("callback carried a code")
                    pendingCallback?.complete(query.getValue("code"))
                }
                else -> log("callback carried neither a code nor an error")
            }
        }
    }

    /**
     * Trade Google's code for tokens. The request itself lives in [CalendarApi]
     * (commonMain, Ktor); what stays here is the part that is genuinely
     * platform-shaped: deciding what a refusal means for the widget.
     */
    private suspend fun exchangeCode(code: String, redirect: String, verifier: String): Tokens? =
        when (val result = api.exchangeCode(code, redirect, verifier)) {
            is OAuthResult.Tokens -> Tokens(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresAtMillis = result.expiresAtMillis,
                account = result.accountHint
            )
            is OAuthResult.Refused -> {
                connectFailure = classifyTokenRefusal(result)
                // The client id rides along because it is the one thing that
                // names the record Google is actually refusing: two consoles
                // can show a "Desktop" client while the id in the binary belongs
                // to another one entirely.
                AppLog.line(
                    "Google token exchange rejected the code for client ${resultForLog()} : " +
                        "${result.error} ${result.description.orEmpty()}".trim()
                )
                null
            }
            OAuthResult.Unreachable -> {
                connectFailure = REASON_AUTH
                AppLog.line("Google token exchange returned no usable response")
                null
            }
        }

    /**
     * Renew an access token. A null refresh token means we cannot renew, which
     * is a dead connection rather than a transient one — the caller treats it as
     * revoked and prompts again.
     */
    private suspend fun refreshed(previous: Tokens): Tokens? {
        val refresh = previous.refreshToken ?: return null
        return when (val result = api.refresh(refresh)) {
            is OAuthResult.Tokens -> previous.copy(
                accessToken = result.accessToken,
                expiresAtMillis = result.expiresAtMillis
            )
            is OAuthResult.Refused -> {
                AppLog.line("Google refused to renew the token: ${result.error}")
                null
            }
            OAuthResult.Unreachable -> {
                AppLog.line("Google token renewal was unreachable")
                null
            }
        }
    }

    /**
     * One day fetch, with every failure mode logged. This used to be a silent
     * null and a 400 from a malformed query read as "not connected" after a
     * sign-in that had entirely succeeded -- the log is what turns that class
     * of bug from an afternoon into a sentence.
     */
    private suspend fun fetchDay(tokens: Tokens): List<CalendarEvent>? =
        when (val result = api.eventsForDay(tokens.accessToken)) {
            is EventsResult.Ok -> {
                log("day fetched: ${result.events.size} events")
                result.events
            }
            is EventsResult.Http -> {
                AppLog.line("Google events request answered HTTP ${result.status}")
                null
            }
            EventsResult.Unreachable -> {
                AppLog.line("Google events request was unreachable")
                null
            }
            EventsResult.Malformed -> {
                AppLog.line("Google events response was not parseable")
                null
            }
        }

    // ── fetching ────────────────────────────────────────────────────────────

    /**
     * One refresh at a time. The button can be clicked faster than a round trip
     * finishes, and two overlapping refreshes would race the token file and, on
     * a server that rotates refresh tokens, invalidate one of them.
     */
    private val refreshMutex = Mutex()

    private suspend fun refreshBlocking() = refreshMutex.withLock {
        refreshBlockingLocked()
    }

    private suspend fun refreshBlockingLocked() {
        var tokens = cached ?: readTokens() ?: run {
            _state.value = CalendarState.Disconnected
            return
        }
        if (tokens.expiresAtMillis - TOKEN_SKEW_MS < System.currentTimeMillis()) {
            tokens = refreshed(tokens) ?: run { revoked(); return }
            cached = tokens
            writeTokens(tokens)
        }

        var events = fetchDay(tokens)
        if (events == null && tokens.refreshToken != null) {
            // Most likely an access token revoked server-side since we cached
            // it: refresh once and retry before giving up.
            tokens = refreshed(tokens) ?: run { revoked(); return }
            cached = tokens
            writeTokens(tokens)
            events = fetchDay(tokens)
        }
        if (events == null) {
            // The caller may be mid-connect (state still Connecting), in which
            // case there is no Connected state to keep stale -- but the tokens
            // are real and on disk, so the honest state is Connected-with-a-
            // stale-day rather than an error that reads as "sign-in failed".
            val previous = _state.value as? CalendarState.Connected
            _state.value = previous?.copy(staleError = REASON_NETWORK) ?: CalendarState.Connected(
                account = tokens.account.ifBlank { "Google" },
                events = emptyList(),
                fetchedAtMillis = 0L,
                staleError = REASON_NETWORK
            )
            return
        }

        val account = tokens.account.ifBlank {
            api.accountEmail(tokens.accessToken) ?: "Google"
        }
        val labelled = tokens.copy(account = account)
        cached = labelled
        writeTokens(labelled)
        writeCachedEvents(events)
        _state.value = CalendarState.Connected(
            account = account,
            events = events,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    /** Google withdrew access (refresh token dead): forget it so the UI can re-prompt. */
    private fun revoked() {
        cached = null
        runCatching { tokenFile.delete() }
        _state.value = CalendarState.Error(REASON_REVOKED)
    }

    // ── token + event store ─────────────────────────────────────────────────

    @Serializable
    private data class Tokens(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAtMillis: Long,
        val account: String = ""
    )

    @Serializable
    private data class CachedEvents(val events: List<CachedEvent> = emptyList())

    @Serializable
    private data class CachedEvent(
        val id: String,
        val summary: String,
        val start: Long,
        val end: Long,
        val allDay: Boolean
    )

    private fun readTokens(): Tokens? = runCatching {
        if (!tokenFile.isFile) return null
        val tokens = json.decodeFromString(Tokens.serializer(), tokenFile.readText())
        if (tokens.accessToken.isBlank()) null else tokens
    }.getOrNull()

    private fun writeTokens(tokens: Tokens) {
        runCatching {
            if (!tokenFile.parentFile.exists()) tokenFile.parentFile.mkdirs()
            tokenFile.writeText(json.encodeToString(Tokens.serializer(), tokens))
            // Holds a bearer token: keep it to the owner.
            Files.setPosixFilePermissions(
                tokenFile.toPath(),
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        }
    }

    private fun readCachedEvents(): List<CalendarEvent> = runCatching {
        if (!eventsFile.isFile) return emptyList()
        json.decodeFromString(CachedEvents.serializer(), eventsFile.readText()).events.map {
            CalendarEvent(it.id, it.summary, it.start, it.end, it.allDay)
        }
    }.getOrElse { emptyList() }

    private fun writeCachedEvents(events: List<CalendarEvent>) {
        runCatching {
            eventsFile.writeText(
                json.encodeToString(
                    CachedEvents.serializer(),
                    CachedEvents(events.map { CachedEvent(it.id, it.summary, it.startMillis, it.endMillis, it.allDay) })
                )
            )
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Google answering a consent redirect with a refusal. */
    private class GoogleAuthException(val error: String, val description: String?) : Exception(error)

    /**
     * Google's "client_secret is missing" means the client in the Cloud console
     * is a Web application rather than a Desktop app. Naming that here beats a
     * generic failure, because everything the user can see *worked*: the browser
     * opened, the sign-in went through, the callback landed.
     */
    /** The id this build exchanges with, for correlating refusals with the console. */
    private fun resultForLog(): String = CLIENT_ID

    private fun classifyTokenRefusal(refusal: OAuthResult.Refused): String {
        val description = refusal.description?.lowercase().orEmpty()
        val wantsASecret = "client_secret" in description ||
            (refusal.error == "invalid_client" && description.contains("secret"))
        return if (wantsASecret) REASON_CLIENT_TYPE else REASON_AUTH
    }

    /**
     * Google's own copy for "this app is not verified yet" arrives as
     * `access_denied`, the same code as a user pressing Cancel. The two are told
     * apart by the description, because the remedy is completely different: one
     * needs a console setting, the other needs the user to stop cancelling.
     * Unrecognised refusals stay [REASON_AUTH] rather than guessing.
     */
    private fun classifyConsentRefusal(failure: GoogleAuthException): String {
        if (failure.error != "access_denied") return REASON_AUTH
        val description = failure.description?.lowercase().orEmpty()
        val blockedByTesting = listOf("not been verified", "not verified", "being tested", "app_notverified")
            .any { it in description }
        return if (blockedByTesting) REASON_NOT_VERIFIED else REASON_CANCELLED
    }

    /** `a=1&b=2` to a map, decoding percent-escapes. */
    private fun queryParams(raw: String?): Map<String, String> =
        raw.orEmpty().split("&").filter { it.isNotBlank() }.associate { pair ->
            val key = pair.substringBefore("=")
            val value = pair.substringAfter("=", "")
            key to runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }

    private fun log(message: String) {
        if (VERBOSE) AppLog.line("calendar $message")
    }

    private fun randomUrlToken(bytes: Int): String = base64Url(ByteArray(bytes).also { random.nextBytes(it) })

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private const val CONSENT_PAGE =
        "<!doctype html><meta charset=utf-8><title>QuickNotch</title>" +
            "<body style=\"font:15px system-ui;background:#111;color:#eee;padding:48px\">" +
            "<h2 style=\"font-weight:600\">QuickNotch is connected</h2>" +
            "<p>You can close this tab and go back to the app.</p>"
}
