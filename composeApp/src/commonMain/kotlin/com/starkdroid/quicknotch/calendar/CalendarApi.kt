package com.starkdroid.quicknotch.calendar

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Every Google Calendar request the app makes, in one multiplatform place.
 *
 * Ktor rather than `HttpURLConnection` for one reason that is not taste, and one
 * that is. The reason that is not taste: the runtime jpackage bundles is
 * jlink-trimmed and contains neither `java.net.http` nor `jdk.httpserver`, so
 * the tidier JDK options are simply absent from the shipped app — and a missing
 * module is a `NoClassDefFoundError` at the first call, which is exactly how the
 * OAuth loopback died silently (pitfall 34). CIO is ordinary bytecode, so it
 * travels with the jars. The reason that is taste: this file has no `java.*` in
 * it, so the network layer moves to a non-JVM target unchanged.
 *
 * Every call is `suspend` and returns a result type instead of throwing, because
 * the caller has three genuinely different failures to tell apart — refused,
 * unreachable, and succeeded — and a null return collapses them (which is how a
 * rejected token exchange once reported whatever the previous failure was).
 */
class CalendarApi(
    private val clientId: String,
    private val client: HttpClient = defaultClient()
) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── OAuth ───────────────────────────────────────────────────────────────

    /** Trade the loopback code for tokens. */
    suspend fun exchangeCode(code: String, redirect: String, verifier: String): OAuthResult {
        val response = postForm(
            TOKEN_ENDPOINT,
            mapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to verifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to redirect
            )
        )
        return response?.toOAuth() ?: OAuthResult.Unreachable
    }

    /** Renew an access token. A null refresh token means we cannot. */
    suspend fun refresh(refreshToken: String): OAuthResult {
        val response = postForm(
            TOKEN_ENDPOINT,
            mapOf(
                "client_id" to clientId,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
        )
        return response?.toOAuth() ?: OAuthResult.Unreachable
    }

    /** The signed-in address, for the settings card. */
    suspend fun accountEmail(accessToken: String): String? {
        val body = runCatching {
            client.get(USERINFO_ENDPOINT) { header("Authorization", "Bearer $accessToken") }.bodyAsText()
        }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(body).jsonObject.string("email") }.getOrNull()
    }

    // ── events ──────────────────────────────────────────────────────────────

    /**
     * Today's events from the primary calendar, already filtered down to what is
     * worth showing: cancelled entries are gone, and so are unnamed ones — a
     * "Busy" block would put a meaningless word on the pill's ticker, so those
     * are left to the day's count alone.
     *
     * Null means the day could not be fetched at all, which the caller treats as
     * "keep the last known day" rather than "the day is empty".
     */
    suspend fun eventsForDay(accessToken: String): List<CalendarEvent>? {
        val (timeMin, timeMax) = todayWindowMillis()
        val response = runCatching {
            client.get(
                "$EVENTS_ENDPOINT" +
                    "?timeMin=${timeMin / 1000}" +
                    "&timeMax=${timeMax / 1000}" +
                    "&singleEvents=true&orderBy=startTime&maxResults=50"
            ) {
                header("Authorization", "Bearer $accessToken")
            }
        }.getOrNull() ?: return null

        // A failed status is "we could not fetch the day", which is NOT the same
        // as "the day is empty" and must not be reported as one. Google's refusal
        // is a JSON body with no `items` key, so parsing alone would quietly turn
        // a revoked token into an empty calendar and wipe the pill's contents
        // instead of keeping the last known day. Null sends the caller down its
        // existing "refresh once, then keep the last known day" path, which is
        // also what catches a genuinely dead token.
        //
        // No logging here on purpose: this file is commonMain, so it has no
        // business reaching for the desktop-only log file.
        if (!response.status.isSuccess()) return null
        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return null

        return runCatching {
            val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
            items.mapNotNull { item ->
                val obj = item.jsonObject
                if (obj.string("status") == "cancelled") return@mapNotNull null
                val id = obj.string("id") ?: return@mapNotNull null
                val summary = obj.string("summary")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val start = obj["start"]?.jsonObject ?: return@mapNotNull null
                val end = obj["end"]?.jsonObject ?: return@mapNotNull null
                val allDay = start["date"]?.jsonPrimitive?.contentOrNullSafe() != null
                val startMillis = (start.string("dateTime") ?: start.string("date"))
                    ?.let { parseEventMillis(it, allDay) } ?: return@mapNotNull null
                // An end we cannot read still tells the user when it started, so
                // the event stays with a nominal hour rather than vanishing.
                val endMillis = (end.string("dateTime") ?: end.string("date"))
                    ?.let { parseEventMillis(it, allDay) } ?: (startMillis + 3_600_000L)
                CalendarEvent(id, summary, startMillis, endMillis, allDay)
            }
        }.getOrNull()
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private suspend fun postForm(endpoint: String, form: Map<String, String>): JsonObject? = runCatching {
        val response = client.submitForm(
            url = endpoint,
            formParameters = Parameters.build { form.forEach { (key, value) -> append(key, value) } }
        )
        // Not expectSuccess: Google's refusals come back as a JSON body that is
        // the most useful thing we have, and turning them into exceptions would
        // throw that detail away.
        json.parseToJsonElement(response.bodyAsText()).jsonObject
    }.getOrNull()

    private fun JsonObject.toOAuth(): OAuthResult {
        val error = string("error")
        if (error != null) return OAuthResult.Refused(error, string("error_description"))
        val access = string("access_token") ?: return OAuthResult.Unreachable
        return OAuthResult.Tokens(
            accessToken = access,
            refreshToken = string("refresh_token"),
            expiresAtMillis = nowMillis() + (string("expires_in")?.toLongOrNull() ?: 3600L) * 1000L,
            accountHint = string("account_hint").orEmpty()
        )
    }

    override fun close() = client.close()

    companion object {
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        const val EVENTS_ENDPOINT = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

        /**
         * Timeouts live here rather than being set per call, and are generous:
         * these run on a half-hourly poll, so a request that needs a second try
         * is cheaper than a day that silently fails to update.
         */
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}

/** The three outcomes a token request can have, kept apart on purpose. */
sealed interface OAuthResult {
    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val accountHint: String
    ) : OAuthResult

    /** Google said no, and said why. */
    data class Refused(val error: String, val description: String?) : OAuthResult

    /** No usable answer at all: offline, or a body that was not JSON. */
    data object Unreachable : OAuthResult
}

// Small readers, kept local so this file needs no serialization data classes for
// responses that are mostly absent. `content` throws on a JSON null, hence the
// safe variant.
private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()
