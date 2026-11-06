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
    // **Web application** client, injected at build time by `generateOAuthConfig`
    // along with its secret: the id is public in OAuth's model, but it names this
    // project's client, so it is not committed either. The type is forced, not
    // preferred: a *Desktop app* client cannot register an https redirect URI at
    // all (the console does not render the field for that type), and the loopback
    // flow it does support is exactly what this app stopped doing in favour of the
    // hosted consent page. Google also asks for a client secret on the token
    // endpoint for every client type now, Desktop clients included, so switching
    // type would not buy freedom from one.
    private const val CLIENT_ID = OAuthConfig.CLIENT_ID

    /**
     * Injected at build time by `generateOAuthConfig`, from CI's
     * `QUICKNOTCH_GOOGLE_CLIENT_SECRET` or a maintainer's ignored
     * `local.properties`. Empty when a build was made without either: such a
     * build says it has no Google client instead of starting a consent screen it
     * cannot finish.
     *
     * A shipped binary can be made to give it up, which is inherent to
     * installed-app OAuth — the same reason gcloud ships its own. It authorizes
     * nothing alone: every token still needs a real user's interactive consent.
     */
    private const val CLIENT_SECRET = OAuthConfig.CLIENT_SECRET

    /** Both halves are injected; a build missing either cannot complete a flow. */
    private val hasClient: Boolean
        get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()

    /**
     * The website owns the consent UI (it has its own editorial voice, and
     * "connect QuickNotch to your Google Calendar" is a story the marketing site
     * tells better than a settings row can). The desktop opens the connect page
     * with the loopback intent encoded in `state`; the page sends the user to
     * Google with `state` set to exactly that string. Google bounces the browser
     * back to the *callback* page, which jumps the tab home with a top-level
     * navigation to the app's loopback server, where the code lands and the
     * exchange happens with the secret that never leaves this process.
     *
     * Only the callback path is a registered redirect URI — the connect page is
     * an entry point, and registering it was never needed or possible.
     *
     * The origin is an environment variable, and the default is deliberately
     * `vite dev` rather than the deployed site: the site does not exist yet, so
     * testing this flow is the *current* job and should not need a flag to
     * remember. Google exempts loopback origins from the HTTPS rule ("Redirect
     * URIs must use the HTTPS scheme, not plain HTTP. Localhost URIs (including
     * localhost IP address URIs) are exempt from this rule"), which is what makes
     * `http://localhost:5173/oauth/callback` a legal redirect URI at all.
     *
     * The port is part of the registered URI, so the dev server must be started
     * via `npm run dev:oauth` (`--strictPort`) — plain `npm run dev` silently
     * falls through to 5174 if 5173 is busy, and every flow then fails as
     * `redirect_uri_mismatch` for a reason that has nothing to do with the code.
     *
     * TO GO LIVE: set [PRODUCTION_WEB_BASE] as [DEFAULT_WEB_BASE], register
     * `https://quicknotch.app/oauth/callback` on the client, and update
     * `calendarAuth` in the site so its fallback matches. The env var still wins,
     * so nothing else changes.
     */
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
     * Google refused the token exchange over the client credential. Almost always
     * a build whose secret is missing, rotated, or has not propagated yet. Kept
     * as its own reason because "everything visible worked, only the exchange was
     * rejected" is baffling without naming the stage that failed.
     */
    const val REASON_CLIENT_CREDENTIALS = "client_credentials"

    /**
     * This binary was built without a Google client secret, so no code can be
     * exchanged. Official installers always carry one; an app built from source
     * needs it supplied at build time (see the README).
     */
    const val REASON_NOT_CONFIGURED = "not_configured"
    /**
     * Tokens exist and work, but the Calendar API itself is not enabled for the
     * project, so every events read is denied with 403. The fix is the API
     * Library in the Cloud console, not anything in this app -- and without a
     * distinct reason the widget would say "couldn't reach Google" for a
     * problem that has nothing to do with reaching Google.
     */
    const val REASON_API_DISABLED = "api_disabled"

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

    /**
     * "Now", sampled once a minute, so anything derived from the wall clock --
     * the pill badge, the schedule tiles -- decays as events end instead of
     * waiting for the next fetch to notice. Minute granularity is finer than
     * any calendar display needs, and one state write per minute change is
     * nothing next to recomposing on every 1s loop pass (pitfall 29).
     */
    private val _minuteTick = MutableStateFlow(System.currentTimeMillis() / 60_000L * 60_000L)
    val minuteTick: StateFlow<Long> = _minuteTick.asStateFlow()

    /**
     * Events whose ticker the user has already seen, so expanding the notch
     * over one dismisses it until the *next* event starts. Session-scoped on
     * purpose: after a restart an in-progress event re-announces itself, which
     * is the honest thing for a notification to do.
     */
    private val seenTickerEventIds = mutableSetOf<String>()

    /**
     * Consume the ticker currently on the pill. Called when the notch expands
     * over it: the detail lives on the calendar page the expansion lands on,
     * and a dismissed ticker stays dismissed until a different event starts.
     */
    fun dismissTicker() {
        _ticker.value?.let { seenTickerEventIds += it.id }
        _ticker.value = null
    }

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
    /**
     * The exact `state` string this attempt sent Google. It carries the loopback
     * port the site needs to jump the browser home, so the echoed value is the
     * only thing the loopback server can trust as coming back from this flow —
     * a callback without it (or with a stale one) is refused, never answered.
     */
    private var expectedState: String? = null
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

    /** Run the web-hosted consent flow: open the connect page, wait for the
     *  site to bounce the code back to the loopback landing strip. */
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
        // unhooked; the loop itself keeps running and resolves null. Seen ids
        // go too, so a reconnect starts the notifications fresh.
        seenTickerEventIds.clear()
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
        val redirect = webCallbackUrl
        return try {
            // Landing strip on loopback: the site's callback page jumps the
            // browser here (a *top-level* navigation, so no CORS/PNA dance)
            // after Google has bounced the code back to the website. The code
            // is exchanged in this process, because the client secret lives
            // here and nowhere else.
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            server.soTimeout = CONNECT_TIMEOUT_MS.toInt()
            listener = server

            // The loopback port rides inside `state`: the consent round-trip
            // goes through the website, and its callback page can only learn
            // *which* port to jump home to from the exact echo Google returns.
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
                    // Offline so we get a refresh token at all. `prompt=consent`
                    // only on a fresh connect: without it Google silently
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
            // NoClassDefFoundError and friends are Errors, not Exceptions, so
            // the clause above misses them entirely — which is exactly how a
            // missing runtime module used to vanish without a trace.
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
                query["code"] != null && query["state"] == expectedState -> {
                    log("callback carried a code with a matching state")
                    pendingCallback?.complete(query.getValue("code"))
                }
                query["code"] != null -> {
                    // A stale or forged jump: the site is meant to echo the
                    // exact state back. Refuse it rather than exchange a code
                    // that does not belong to this attempt.
                    log("callback carried a code but a mismatched state; refusing")
                    pendingCallback?.completeExceptionally(
                        GoogleAuthException("state_mismatch", "callback state did not match this attempt")
                    )
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

    /**
     * One day fetch, with every failure mode logged. This used to be a silent
     * null and a 400 from a malformed query read as "not connected" after a
     * sign-in that had entirely succeeded -- the log is what turns that class
     * of bug from an afternoon into a sentence.
     */
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

    /**
     * A 403 whose body talks about a disabled or never-used API is the project
     * missing the Calendar API enablement -- a console step, nothing in the
     * app, and worth its own message because "couldn't reach Google" sends the
     * user looking at their router.
     */
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

    // ── fetching ────────────────────────────────────────────────────────────

    /**
     * One refresh at a time. The button can be clicked faster than a round trip
     * finishes, and two overlapping refreshes would race the token file and, on
     * a server that rotates refresh tokens, invalidate one of them.
     */
    private val refreshMutex = Mutex()

    /**
     * Observable "a day fetch is running", for the spinner that replaces the
     * Refresh chip and for the notch page's loading state. The fetch itself
     * never blocks any UI: it runs on the IO scope and only this flag moves.
     */
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
            // The caller may be mid-connect (state still Connecting), in which
            // case there is no Connected state to keep stale -- but the tokens
            // are real and on disk, so the honest state is Connected-with-a-
            // stale-day rather than an error that reads as "sign-in failed".
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
     * The id this build exchanges with, for correlating refusals with the console.
     * A secret-shaped refusal is only interpretable next to it: the same error
     * means a wrong client on an old build and a rotated or not-yet-propagated
     * secret on this one.
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

/**
 * The pill badge's number: today's events that are still upcoming -- running
 * right now counts, one that already ended does not. That is both what the
 * user reads on Google Calendar for the day ("2 upcoming events") and what the
 * notch can still announce; a whole-window count would badge the pill with
 * December while the day itself has nothing going on.
 */
fun calendarUpcomingCount(state: CalendarState, nowMillis: Long): Int =
    (state as? CalendarState.Connected)?.events?.todayUpcomingCount(nowMillis = nowMillis) ?: 0
