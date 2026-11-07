package com.starkdroid.quicknotch.calendar

import com.starkdroid.quicknotch.OAuthConfig
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
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

    /** Connected; [events] is the last successful or cached list, [staleError] marks a failed refresh. */
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
     * [reason] is a stable key the UI localises; a raw server message would leak internals into settings.
     */
    data class Error(val reason: String) : CalendarState
}

/**
 * Google Calendar, read-only, via OAuth with PKCE. Tokens and the last event list are cached on
 * disk, and a revoked grant drops back to [CalendarState.Disconnected] instead of looping failures.
 */
object GoogleCalendar {
    // Web application client, injected at build time (the id is not committed). A Desktop client
    // cannot register an https redirect URI at all.
    private const val CLIENT_ID = OAuthConfig.CLIENT_ID

    /**
     * Injected at build time; empty means the build says it has no Google client instead of starting
     * a flow it cannot finish. A shipped binary can be made to give it up, but it authorizes nothing alone.
     */
    private const val CLIENT_SECRET = OAuthConfig.CLIENT_SECRET

    private val hasClient: Boolean
        get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()

    // Only /oauth/callback is a registered redirect URI, and the port is part of it: the dev server
    // must use `npm run dev:oauth` (--strictPort) or every flow fails as redirect_uri_mismatch.
    private const val WEB_BASE_ENV = "QUICKNOTCH_WEB_BASE"
    private const val PRODUCTION_WEB_BASE = "https://quicknotch.app"
    private const val DEFAULT_WEB_BASE = "http://localhost:5173"

    private val webBase: String by lazy {
        val override = System.getenv(WEB_BASE_ENV)?.trim().orEmpty()
        (if (override.isEmpty()) DEFAULT_WEB_BASE else override).trimEnd('/')
    }

    private val webConnectUrl: String get() = "$webBase/connect"
    private val webCallbackUrl: String get() = "$webBase/oauth/callback"

    /** Read-only calendar, plus the address used to label the connection. */
    private const val SCOPE =
        "https://www.googleapis.com/auth/calendar.readonly " +
            "https://www.googleapis.com/auth/userinfo.email"

    private const val POLL_MS = 30 * 60 * 1000L
    private const val TOKEN_SKEW_MS = 60_000L

    private const val CONNECT_TIMEOUT_MS = 5 * 60 * 1000L

    /** Per-stage tracing for the consent handshake, the one flow that is opaque from outside. */
    private const val VERBOSE = true

    /** Reason keys; the UI maps them to localized strings. */
    const val REASON_AUTH = "auth"
    const val REASON_NETWORK = "network"
    const val REASON_REVOKED = "revoked"
    /** The user closed the consent screen or declined the app. */
    const val REASON_CANCELLED = "cancelled"
    /**
     * The consent screen is still in Testing and this account is not a test user; Google sends
     * `access_denied`, same as a plain decline, so the two are told apart by the description.
     */
    const val REASON_NOT_VERIFIED = "not_verified"
    /** Google refused the token exchange over the client credential: a missing, rotated, or unpropagated secret. */
    const val REASON_CLIENT_CREDENTIALS = "client_credentials"

    /** This build has no Google client secret, so no code can be exchanged. */
    const val REASON_NOT_CONFIGURED = "not_configured"
    /** Tokens work but the Calendar API is not enabled for the project, so every read gets a 403. */
    const val REASON_API_DISABLED = "api_disabled"

    /** One client for the process: it owns a connection pool and a selector thread. */
    private val api = CalendarApi(CLIENT_ID, CLIENT_SECRET)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val random = SecureRandom()

    private val _state = MutableStateFlow<CalendarState>(CalendarState.Disconnected)
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    /** The event whose ticker is up right now, resolved here so the notch only reads a flow. */
    private val _ticker = MutableStateFlow<CalendarEvent?>(null)
    val ticker: StateFlow<CalendarEvent?> = _ticker.asStateFlow()

    /**
     * "Now" sampled once a minute, so derived values decay as events end; one write per minute
     * beats recomposing on every 1s loop pass.
     */
    private val _minuteTick = MutableStateFlow(System.currentTimeMillis() / 60_000L * 60_000L)
    val minuteTick: StateFlow<Long> = _minuteTick.asStateFlow()

    // Session-scoped on purpose: after a restart an in-progress event re-announces itself.
    private val seenTickerEventIds = mutableSetOf<String>()

    /** Consume the ticker on the pill; it stays dismissed until a different event starts. */
    fun dismissTicker() {
        _ticker.value?.let { seenTickerEventIds += it.id }
        _ticker.value = null
    }

    private val tokenFile: File get() = File(AppPaths.configDir, "calendar.json")
    private val eventsFile: File get() = File(AppPaths.configDir, "calendar-events.json")

    private var cached: Tokens? = null

    /** Why the last [connect] attempt gave up, as one of the REASON_ keys. */
    private var connectFailure: String = REASON_AUTH
    private var connectJob: Job? = null
    private var pendingCallback: CompletableFuture<String>? = null
    /**
     * The exact `state` string this attempt sent Google: it carries the loopback port, so a
     * callback without it (or with a stale one) is refused.
     */
    private var expectedState: String? = null
    private var cancelledByUser = false
    private var tickerJob: Job? = null
    private var pollJob: Job? = null

    /** Start the poller. Idempotent; called once from the app. */
    fun start() {
        if (pollJob != null) return

        // Armed before the token check: connecting happens later from settings, so starting only on
        // the already-connected path would leave the ticker permanently empty.
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

    /** A 1s tick that writes only when the answer changes; an unchanged value costs no recomposition. */
    private fun startTicker() {
        if (tickerJob != null) return
        tickerJob = scope.launch {
            while (isActive) {
                val nowMillis = System.currentTimeMillis()
                val nowMinute = nowMillis / 60_000L * 60_000L
                if (nowMinute != _minuteTick.value) _minuteTick.value = nowMinute
                val next = (_state.value as? CalendarState.Connected)
                    ?.tickerEventAt(nowMillis)
                    ?.takeIf { it.id !in seenTickerEventIds }
                if (next != _ticker.value) _ticker.value = next
                delay(1000L)
            }
        }
    }

    /** Open the web-hosted consent page and wait for the site to bounce the code back. */
    fun connect() {
        if (_state.value is CalendarState.Connecting) return
        if (!hasClient) {
            // A build made without the injected client, not a user mistake: say
            // what is missing before opening a browser that could never finish.
            connectFailure = REASON_NOT_CONFIGURED
            _state.value = CalendarState.Error(REASON_NOT_CONFIGURED)
            AppLog.line("Google sign-in skipped: this build has no Google client")
            return
        }
        cancelledByUser = false
        // Start each attempt from the generic reason: a path that returns null without setting one
        // used to leave the previous attempt's failure on screen.
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
            // Connected the moment tokens exist: a slow or failing first fetch must not read as a
            // sign-in that did not finish.
            _state.value = CalendarState.Connected(
                account = tokens.account.ifBlank { "Google" },
                events = emptyList(),
                fetchedAtMillis = 0L
            )
            // Consent succeeding proves nothing; the first fetch is the real test.
            refreshBlocking()
        }
    }

    /** Give up on a sign-in in progress, instead of spinning out the full five-minute budget. */
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
        // Without this the pill keeps scrolling an event from the calendar just unhooked.
        seenTickerEventIds.clear()
        _ticker.value = null
    }

    /** The settings widget's "Refresh now". */
    fun refresh() {
        scope.launch { refreshBlocking() }
    }


    /**
     * The whole consent handshake in one try: a throw while setting up the listener must not leave
     * the state stranded on [CalendarState.Connecting].
     */
    private suspend fun authorize(): Tokens? {
        val verifier = randomUrlToken(64)
        val challenge = base64Url(sha256(verifier))
        val started = System.currentTimeMillis()
        val received = CompletableFuture<String>()
        var listener: ServerSocket? = null
        var listenerThread: Thread? = null
        val redirect = webCallbackUrl
        return try {
            // The site's callback page jumps the browser here with a top-level navigation, and the
            // code is exchanged in this process because the client secret lives here.
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            server.soTimeout = CONNECT_TIMEOUT_MS.toInt()
            listener = server

            // The loopback port rides inside `state`, the only echo Google returns.
            val state = base64Url(
                "{\"p\":${server.localPort},\"s\":\"${randomUrlToken(16)}\"}"
                    .toByteArray(Charsets.UTF_8)
            )
            expectedState = state

            // One request, one answer, then done: the jump is a single GET
            // carrying the callback's code and the echoed state in the query.
            listenerThread = Thread({
                runCatching { serveCallback(server) }
                    .onFailure { received.completeExceptionally(it) }
            }, "quicknotch-oauth-callback").apply {
                isDaemon = true
                start()
            }
            pendingCallback = received
            log("consent landing on 127.0.0.1:${server.localPort}")
            log("consent UI served from $webBase")

            // Consent UI lives on the website now; this URL is everything the
            // page needs to run the flow and bring the browser back here.
            AppLauncher.browse(
                webConnectUrl + "?" + listOf(
                    "client_id" to CLIENT_ID,
                    "redirect_uri" to redirect,
                    "port" to server.localPort.toString(),
                    "state" to state,
                    "scope" to SCOPE,
                    // Offline for a refresh token at all, plus prompt=consent: without it Google
                    // withholds the refresh token for an already-authorised app.
                    "access_type" to "offline",
                    "prompt" to "consent",
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256"
                ).joinToString("&") { (k, v) -> "$k=" + urlEncode(v) }
            )
            log("connect page opened; waiting for the callback (${CONNECT_TIMEOUT_MS / 60_000} min budget)")

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
            // NoClassDefFoundError is an Error, not an Exception, so the clause above misses it entirely.
            connectFailure = REASON_AUTH
            log("consent died: $e")
            AppLog.line("Google sign-in died: $e")
            null
        } finally {
            pendingCallback = null
            expectedState = null
            runCatching { listener?.close() }
            listenerThread?.interrupt()
        }
    }

    /**
     * Hand-rolled on a plain [ServerSocket]: the jlink runtime has no `jdk.httpserver`, so HttpServer
     * threw `NoClassDefFoundError` in the packaged app and nowhere else.
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
                // Google answers with `error=...` on a decline; read it instead of sitting out the
                // whole timeout on a callback that can never carry a code.
                query["error"] != null -> {
                    log("callback carried ${query.getValue("error")}")
                    pendingCallback?.completeExceptionally(
                        GoogleAuthException(query.getValue("error"), query["error_description"])
                    )
                }
                query["code"] != null && query["state"] == expectedState -> {
                    log("callback carried a code with a matching state")
                    pendingCallback?.complete(query.getValue("code"))
                }
                query["code"] != null -> {
                    // A stale or forged jump: refuse a code that does not belong to this attempt.
                    log("callback carried a code but a mismatched state; refusing")
                    pendingCallback?.completeExceptionally(
                        GoogleAuthException("state_mismatch", "callback state did not match this attempt")
                    )
                }
                else -> log("callback carried neither a code nor an error")
            }
        }
    }

    /** What stays here is the platform-shaped part: what a refusal means for the widget. */
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
                // The client id goes into the log: two consoles can show a "Desktop" client while
                // the id in the binary belongs to another one.
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

    /** A null refresh token means we cannot renew: a dead connection, so the caller treats it as revoked. */
    private suspend fun refreshed(previous: Tokens): Tokens? {
        // Nothing to send without the build's secret; `revoked()` turns that into
        // the "no client configured" message and keeps the stored grant.
        val refresh = previous.refreshToken?.takeIf { hasClient } ?: return null
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

    /** One day fetch, with every failure mode logged. */
    private suspend fun fetchDay(tokens: Tokens): FetchOutcome {
        return when (val result = api.events(tokens.accessToken, fetchFromMillis())) {
            is EventsResult.Ok -> {
                log("day fetched: ${result.events.size} upcoming events from today onward")
                FetchOutcome.Ok(result.events)
            }
            is EventsResult.Http -> {
                // Google's body is the diagnosis; print it verbatim so the log
                // says "enable the API at <url>" instead of a bare status code.
                AppLog.line(
                    "Google events request answered HTTP ${result.status}" +
                        (result.detail?.let { ": $it" } ?: "")
                )
                FetchOutcome.Failed(classifyFetchFailure(result.status, result.detail))
            }
            EventsResult.Unreachable -> {
                AppLog.line("Google events request was unreachable")
                FetchOutcome.Failed(REASON_NETWORK)
            }
            EventsResult.Malformed -> {
                AppLog.line("Google events response was not parseable")
                FetchOutcome.Failed(REASON_NETWORK)
            }
        }
    }

    /** A 403 naming a disabled API is a missing console step, not a network problem. */
    private fun classifyFetchFailure(status: Int, detail: String?): String {
        if (status == 403) {
            val d = detail?.lowercase().orEmpty()
            if ("disabled" in d || "has not been used" in d) return REASON_API_DISABLED
        }
        return REASON_NETWORK
    }

    /** The day, or the reason key it could not be fetched under. */
    private sealed interface FetchOutcome {
        data class Ok(val events: List<CalendarEvent>) : FetchOutcome
        data class Failed(val reason: String) : FetchOutcome
    }

    /** One refresh at a time: overlapping refreshes race the token file and a rotating server invalidates one. */
    private val refreshMutex = Mutex()

    /** Observable "a day fetch is running", for the spinner and the page's loading state. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private suspend fun refreshBlocking() {
        _refreshing.value = true
        try {
            refreshMutex.withLock { refreshBlockingLocked() }
        } finally {
            _refreshing.value = false
        }
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

        var outcome = fetchDay(tokens)
        if (outcome is FetchOutcome.Failed && tokens.refreshToken != null) {
            // Most likely an access token revoked server-side since we cached
            // it: refresh once and retry before giving up.
            tokens = refreshed(tokens) ?: run { revoked(); return }
            cached = tokens
            writeTokens(tokens)
            outcome = fetchDay(tokens)
        }
        if (outcome is FetchOutcome.Failed) {
            // The caller may be mid-connect: the tokens are real and on disk, so the honest state
            // is Connected with a stale day, not an error that reads as "sign-in failed".
            val previous = _state.value as? CalendarState.Connected
            _state.value = previous?.copy(staleError = outcome.reason) ?: CalendarState.Connected(
                account = tokens.account.ifBlank { "Google" },
                events = emptyList(),
                fetchedAtMillis = 0L,
                staleError = outcome.reason
            )
            return
        }
        val events = (outcome as FetchOutcome.Ok).events

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
        if (!hasClient) {
            // Not a revoked grant: this build cannot renew at all, so keep the
            // stored tokens for a build that can.
            _state.value = CalendarState.Error(REASON_NOT_CONFIGURED)
            return
        }
        cached = null
        runCatching { tokenFile.delete() }
        _state.value = CalendarState.Error(REASON_REVOKED)
    }

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


    /** Google answering a consent redirect with a refusal. */
    private class GoogleAuthException(val error: String, val description: String?) : Exception(error)

    /**
     * The client id this build exchanges with, so a refusal can be read
     * against the console.
     */
    private fun resultForLog(): String = CLIENT_ID

    private fun classifyTokenRefusal(refusal: OAuthResult.Refused): String {
        val description = refusal.description?.lowercase().orEmpty()
        val wantsASecret = "client_secret" in description ||
            (refusal.error == "invalid_client" && description.contains("secret"))
        return when {
            !wantsASecret -> REASON_AUTH
            !hasClient -> REASON_NOT_CONFIGURED
            else -> REASON_CLIENT_CREDENTIALS
        }
    }

    /**
     * Consent refusal and Cancel share access_denied; the description tells
     * them apart. Unknown refusals stay REASON_AUTH.
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

/**
 * Today's still-upcoming events: a running one counts, a finished one
 * does not.
 */
fun calendarUpcomingCount(state: CalendarState, nowMillis: Long): Int =
    (state as? CalendarState.Connected)?.events?.todayUpcomingCount(nowMillis = nowMillis) ?: 0
