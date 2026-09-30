package com.starkdroid.quicknotch.calendar

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Instant
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
    private val clientSecret: String,
    private val client: HttpClient = defaultClient()
) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── OAuth ───────────────────────────────────────────────────────────────

    /**
     * Trade the loopback code for tokens.
     *
     * The secret goes along despite this being the documented PKCE-no-secret
     * desktop flow, because the endpoint now rejects every code grant without
     * it — Desktop clients included (verified 2026-09 against a real code: the
     * identical request without the secret answers
     * `invalid_request: client_secret is missing`, and Google's own gcloud
     * client behaves the same). PKCE stays: it is what binds the exchange to
     * *this* app's authorization request. The secret by itself authorizes
     * nothing — tokens still require a real user's consent.
     */
    suspend fun exchangeCode(code: String, redirect: String, verifier: String): OAuthResult {
        val response = postForm(
            TOKEN_ENDPOINT,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
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
                "client_secret" to clientSecret,
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
    /**
     * Today's events, with the failure named rather than flattened into a null.
     *
     * The flattening cost a real bug: `timeMin` went out as epoch seconds, which
     * Google's API answers with a 400 ("must be an RFC3339 timestamp") — and a
     * null return made that indistinguishable from every other failure, so the
     * widget showed "not connected" after a sign-in that had fully succeeded,
     * and nothing was logged anywhere. The timestamps are RFC3339 now; the
     * reason travels back to the caller, which logs it.
     */
    suspend fun events(accessToken: String, fromMillis: Long): EventsResult {
        val timeMin = fromMillis
        // Instant.toString() is RFC3339 in UTC, which is the same instant as
        // local midnight — the day boundary is what Google filters on.
        val response = runCatching {
            client.get(
                "$EVENTS_ENDPOINT" +
                    // No timeMax: everything upcoming, the schedule view's
                    // exact query. maxResults is the payload guard.
                    "?timeMin=${Instant.fromEpochMilliseconds(timeMin)}" +
                    "&singleEvents=true&orderBy=startTime&maxResults=250"
            ) {
                header("Authorization", "Bearer $accessToken")
            }
        }.getOrNull() ?: return EventsResult.Unreachable

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
        if (!response.status.isSuccess()) {
            // Google's error body is the most useful thing in the response and
            // it costs nothing to read -- the 403 for a disabled API names the
            // exact console page that fixes it.
            val detail = runCatching {
                json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]
                    ?.jsonObject?.string("message")
            }.getOrNull()
            return EventsResult.Http(response.status.value, detail)
        }
        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return EventsResult.Malformed

        return runCatching {
            val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
                ?: return@runCatching EventsResult.Ok(emptyList())
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
            }.let { EventsResult.Ok(it) }
        }.getOrElse { EventsResult.Malformed }
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

/** The four outcomes of a day fetch, each diagnosable at the caller. */
sealed interface EventsResult {
    data class Ok(val events: List<CalendarEvent>) : EventsResult

    /**
     * Google answered, and said no. [detail] is Google's own message from the
     * body, because the body is the diagnosis: a 403 whose body says "Calendar
     * API has not been used in project ... before or it is disabled" is the
     * project's API Library, not a token problem, and the two remedies are in
     * completely different places.
     */
    data class Http(val status: Int, val detail: String? = null) : EventsResult

    /** No usable answer at all: offline, timeout, TLS. */
    data object Unreachable : EventsResult

    /** An answer that was not the JSON we know how to read. */
    data object Malformed : EventsResult
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
