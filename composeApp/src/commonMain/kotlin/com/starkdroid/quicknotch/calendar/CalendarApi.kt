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
 * Every Google Calendar request, over Ktor CIO because the jlink runtime
 * has no java.net.http and a missing module is a NoClassDefFoundError.
 */
class CalendarApi(
    private val clientId: String,
    private val clientSecret: String,
    private val client: HttpClient = defaultClient()
) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }


    /**
     * Trades the loopback code for tokens. Google now requires the client
     * secret on every code grant, Desktop clients included; PKCE still binds
     * the exchange to this request.
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


    /**
     * Today's events with cancelled and unnamed entries removed. A failed
     * fetch returns a named failure, never an empty day.
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

        // A failed status is not an empty day: return a named failure so the
        // caller keeps the last known day.
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
     * Google's own message from the body: a 403 naming a disabled Calendar
     * API is a project setting, not a token problem.
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
