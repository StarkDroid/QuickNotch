package com.starkdroid.quicknotch.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Open-Meteo and ip-api, both keyless, over Ktor CIO for the jlink
 * reason. Result types keep refusals, unreachable networks and bad
 * payloads apart so the last known day can stand.
 */
class WeatherApi(
    private val client: HttpClient = defaultClient()
) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }


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


    /**
     * Current conditions plus a 7-day outlook; timezone=auto makes today the
     * place's day.
     */
    suspend fun forecast(latitude: Double, longitude: Double, fahrenheit: Boolean): WeatherResult {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val body = runCatching {
            client.get(
                "$FORECAST_ENDPOINT?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,is_day" +
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
            code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
            // Open-Meteo answers 1/0; absent (older cache shape) means day.
            isDay = current["is_day"]?.jsonPrimitive?.intOrNull != 0
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


    /**
     * Keyless Open-Meteo geocoding for the manual-location picker.
     */
    suspend fun searchPlaces(query: String): GeocodeResult {
        if (query.isBlank()) return GeocodeResult.Ok(emptyList())
        val body = fetchGeocodeBody(query) ?: return GeocodeResult.Unreachable
        return parsePlaces(body)
    }

    private suspend fun fetchGeocodeBody(query: String): String? = runCatching {
        client.get(GEOCODE_ENDPOINT) {
            parameter("name", query)
            parameter("count", 6)
            parameter("language", "en")
            parameter("format", "json")
        }.bodyAsText()
    }.getOrNull()

    /** No match is `{}` with no `results` key — an empty list, not a failure. */
    private fun parsePlaces(body: String): GeocodeResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return GeocodeResult.Malformed
        val places = root["results"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val obj = runCatching { entry.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val admin = obj["admin1"]?.jsonPrimitive?.content.orEmpty()
            val country = obj["country"]?.jsonPrimitive?.content.orEmpty()
            val label = listOf(name, admin, country)
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            WeatherPlace(name = label, latitude = lat, longitude = lon)
        }
        return GeocodeResult.Ok(places)
    }

    override fun close() = client.close()

    companion object {
        const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val GEO_ENDPOINT = "http://ip-api.com/json/"
        const val GEOCODE_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"

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

/**
 * A user-picked weather place: display label plus coordinates. Stored in the
 * config when set (null there means the automatic IP position), so it must
 * stay `@Serializable` with defaults — old files simply load null.
 */
@Serializable
data class WeatherPlace(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

/** Conditions right now. Temperatures are in the requested unit. */
data class WeatherNow(
    val temperature: Double,
    val humidity: Int? = null,
    val code: Int = 0,
    /** Open-Meteo `is_day` flag — picks the sun vs. moon glyph. Defaults to day. */
    val isDay: Boolean = true
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

/** The outcomes of a place search. */
sealed interface GeocodeResult {
    data class Ok(val places: List<WeatherPlace>) : GeocodeResult
    data object Unreachable : GeocodeResult
    data object Malformed : GeocodeResult
}

/** The outcomes of a forecast fetch. */
sealed interface WeatherResult {
    data class Ok(val data: WeatherData) : WeatherResult
    data class Http(val status: Int) : WeatherResult
    data object Unreachable : WeatherResult
    data object Malformed : WeatherResult
}
