package com.starkdroid.quicknotch.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Every weather request the app makes, in one multiplatform place.
 *
 * Open-Meteo rather than any keyed provider for one reason that is not taste:
 * there is no key to provision, rotate, or leak out of a shipped binary — the
 * forecast endpoint answers anonymous GETs, so a fresh install shows weather
 * with zero setup, exactly like the notch itself needs no account. Ktor CIO
 * for the same jlink reason as the calendar API (only `java.base`-level
 * modules ship in the packaged runtime, so the transport must be ordinary
 * bytecode). This file has no `java.*` in it.
 *
 * Location is IP-based (ip-api, city granularity, also keyless): the notch
 * has no location-permission plumbing and must not ask for any. A VPN exit
 * simply reports its own city, which is honest enough for a glanceable pill.
 *
 * Every call is `suspend` and returns a result type instead of throwing — a
 * refusal, an unreachable network, and a malformed payload are three
 * different answers, and the controller keeps the last known day for all of
 * them rather than blanking the pill.
 */
class WeatherApi(
    private val client: HttpClient = defaultClient()
) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── location ─────────────────────────────────────────────────────────────

    /**
     * City-granularity position from the egress IP. Free tier is HTTP-only
     * (no TLS on the keyless endpoint), which Ktor handles like anything else.
     */
    suspend fun locate(): LocationResult {
        val body = runCatching {
            client.get("$GEO_ENDPOINT?fields=status,message,lat,lon,city,countryCode").bodyAsText()
        }.getOrNull() ?: return LocationResult.Unreachable
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return LocationResult.Malformed
        if (obj["status"]?.jsonPrimitive?.content != "success") return LocationResult.Refused
        val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return LocationResult.Malformed
        val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull ?: return LocationResult.Malformed
        val city = obj["city"]?.jsonPrimitive?.content.orEmpty()
        return LocationResult.Found(latitude = lat, longitude = lon, city = city)
    }

    // ── forecast ─────────────────────────────────────────────────────────────

    /**
     * Current conditions plus a 7-day outlook for one position. `fahrenheit`
     * switches the temperature unit; everything else (%, UV index) is
     * unitless. `timezone=auto` makes day boundaries local to the position,
     * so "today" on the page is the place's today, not the machine's.
     */
    suspend fun forecast(latitude: Double, longitude: Double, fahrenheit: Boolean): WeatherResult {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val body = runCatching {
            client.get(
                "$FORECAST_ENDPOINT?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
                    "precipitation_probability_max,uv_index_max" +
                    "&timezone=auto&forecast_days=7$unit"
            ).bodyAsText()
        }.getOrNull() ?: return WeatherResult.Unreachable
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return WeatherResult.Malformed
        // An error payload carries no `current` key — same trap as the
        // calendar's refusal body with no `items`: parsing alone would turn a
        // refusal into a blank-but-valid day, so check the status first and
        // treat "could not fetch" and "nothing to show" as different answers.
        val current = root["current"]?.jsonObject ?: return WeatherResult.Malformed
        val daily = root["daily"]?.jsonObject ?: return WeatherResult.Malformed
        val now = WeatherNow(
            temperature = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull
                ?: return WeatherResult.Malformed,
            humidity = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull,
            code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
        )
        val dates = daily["time"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val codes = daily["weather_code"]?.jsonArray.orEmpty().map { it.jsonPrimitive.intOrNull ?: 0 }
        val highs = daily["temperature_2m_max"]?.jsonArray.orEmpty().map { it.jsonPrimitive.doubleOrNull }
        val lows = daily["temperature_2m_min"]?.jsonArray.orEmpty().map { it.jsonPrimitive.doubleOrNull }
        val precip = daily["precipitation_probability_max"]?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.intOrNull }
        val uv = daily["uv_index_max"]?.jsonArray.orEmpty().map { it.jsonPrimitive.doubleOrNull }
        if (dates.isEmpty()) return WeatherResult.Malformed
        val days = dates.indices.map { i ->
            WeatherDay(
                dateIso = dates[i],
                code = codes.getOrElse(i) { 0 },
                high = highs.getOrNull(i),
                low = lows.getOrNull(i),
                precipitationProbability = precip.getOrNull(i),
                uvIndexMax = uv.getOrNull(i)
            )
        }
        return WeatherResult.Ok(
            WeatherData(
                now = now,
                days = days,
                fahrenheit = fahrenheit,
                fetchedAtMillis = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override fun close() = client.close()

    companion object {
        const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val GEO_ENDPOINT = "http://ip-api.com/json/"

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

/** One position fix: city-granularity, from the egress IP. */
data class GeoPosition(
    val latitude: Double,
    val longitude: Double,
    val city: String = ""
)

/** Conditions right now. Temperatures are in the requested unit. */
data class WeatherNow(
    val temperature: Double,
    val humidity: Int? = null,
    val code: Int = 0
)

/**
 * One outlook day. `dateIso` is `yyyy-MM-dd` in the *position's* timezone;
 * the UI layer renders the weekday name in the system locale.
 */
data class WeatherDay(
    val dateIso: String,
    val code: Int = 0,
    val high: Double? = null,
    val low: Double? = null,
    val precipitationProbability: Int? = null,
    val uvIndexMax: Double? = null
)

/** A fetched forecast: position label, current conditions, 7-day outlook. */
data class WeatherData(
    val now: WeatherNow,
    val days: List<WeatherDay> = emptyList(),
    val fahrenheit: Boolean = false,
    val place: String = "",
    val fetchedAtMillis: Long = 0L
)

/** The three outcomes of a location lookup. */
sealed interface LocationResult {
    data class Found(val latitude: Double, val longitude: Double, val city: String = "") : LocationResult
    data object Refused : LocationResult
    data object Unreachable : LocationResult
    data object Malformed : LocationResult
}

/** The outcomes of a forecast fetch. */
sealed interface WeatherResult {
    data class Ok(val data: WeatherData) : WeatherResult
    data class Http(val status: Int) : WeatherResult
    data object Unreachable : WeatherResult
    data object Malformed : WeatherResult
}
