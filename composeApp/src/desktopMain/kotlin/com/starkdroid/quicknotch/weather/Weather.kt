package com.starkdroid.quicknotch.weather

import com.starkdroid.quicknotch.datasource.AppLog
import com.starkdroid.quicknotch.datasource.AppPaths
import java.io.File
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
import kotlin.math.abs

/**
 * The app-wide weather feed: one IP-geolocated position plus its Open-Meteo
 * forecast, polled on a half-hourly loop with a disk cache, observed by the
 * quickview chip, the expanded page, and the settings card.
 *
 * Failure semantics mirror every other poller in the app: a failed fetch
 * leaves the last known day in place (stale but real) and only a launch with
 * no cache at all reports [WeatherState.Failed]. Nothing here throws past the
 * scope — the loop survives any single bad round trip.
 */
sealed interface WeatherState {
    data object Loading : WeatherState
    data class Ready(val data: WeatherData) : WeatherState
    data object Failed : WeatherState
}

object Weather {

    private const val POLL_MS = 30L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = WeatherApi()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private val _fetching = MutableStateFlow(false)
    val fetching: StateFlow<Boolean> = _fetching.asStateFlow()

    private var pollJob: Job? = null
    private var position: GeoPosition? = null
    // Last manual place applied (dedupes config re-emits). Null = automatic.
    private var lastManualPlace: WeatherPlace? = null

    private val cacheFile: File get() = File(AppPaths.configDir, "weather.json")

    /**
     * Start the poller. Idempotent; called once from the app. A manual place
     * seeds the position up front so the first fetch never detours through the
     * IP lookup; without one the cached fix (if any) wins as before.
     */
    fun start(manualPlace: WeatherPlace? = null) {
        if (pollJob != null) return
        lastManualPlace = manualPlace
        if (manualPlace != null) {
            position = GeoPosition(manualPlace.latitude, manualPlace.longitude, manualPlace.name)
        }
        // Show the cached day immediately so a cold start paints weather on
        // the first frame, then refresh in the background — but only when the
        // cache belongs to the active position. A stale fix from another city
        // (e.g. after picking a new place) must never paint under the new one.
        readCache()?.let { cached ->
            val sameFix = position?.let {
                abs(it.latitude - cached.lat) < 0.005 && abs(it.longitude - cached.lon) < 0.005
            } ?: true
            if (sameFix) {
                position = position ?: GeoPosition(cached.lat, cached.lon, cached.city)
                _state.value = WeatherState.Ready(cached.toData())
            }
        }
        pollJob = scope.launch {
            refreshBlocking()
            while (isActive) {
                delay(POLL_MS)
                refreshBlocking()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * One immediate fetch (settings refresh). Serialized by a mutex because
     * the button outruns the round trip and overlapping refreshes would race
     * the cache file.
     */
    fun refresh() {
        scope.launch {
            refreshMutex.withLock { refreshBlocking() }
        }
    }

    /**
     * Apply a config-side place change at runtime. A pick replaces the
     * position outright; clearing it (null) drops the fix so the next fetch
     * re-locates over IP. Either way one immediate fetch follows, under the
     * same mutex as the settings refresh. Re-emits of the same place are
     * ignored — config flows re-emit on every unrelated write.
     */
    fun setManualPlace(place: WeatherPlace?) {
        if (place == lastManualPlace) return
        lastManualPlace = place
        position = place?.let { GeoPosition(it.latitude, it.longitude, it.name) }
        scope.launch {
            refreshMutex.withLock { refreshBlocking() }
        }
    }

    /**
     * City/state search for the settings picker. Empty query or any failure
     * answers with no places rather than an error — the picker just shows
     * "no places found".
     */
    suspend fun searchPlaces(query: String): List<WeatherPlace> =
        when (val found = api.searchPlaces(query.trim())) {
            is GeocodeResult.Ok -> found.places
            else -> {
                AppLog.line("QuickNotch weather place search failed: $found")
                emptyList()
            }
        }

    /**
     * Resolve the position (cached fix wins, IP lookup only when there is
     * none) and fetch the forecast into it. Null keeps the previous state —
     * "could not fetch" is never reported as an empty sky.
     */
    private suspend fun refreshBlocking() {
        val fix = position ?: locate() ?: run {
            if (_state.value !is WeatherState.Ready) _state.value = WeatherState.Failed
            return
        }
        position = fix
        _fetching.value = true
        try {
            // Always fetched metric; Fahrenheit is a display conversion, so a
            // unit toggle never costs a round trip.
            when (val outcome = api.forecast(fix.latitude, fix.longitude, fahrenheit = false)) {
                is WeatherResult.Ok -> {
                    val data = outcome.data.copy(place = fix.city)
                    writeCache(fix, data)
                    _state.value = WeatherState.Ready(data)
                }
                else -> {
                    AppLog.line("QuickNotch weather fetch failed: $outcome")
                    if (_state.value !is WeatherState.Ready) _state.value = WeatherState.Failed
                }
            }
        } finally {
            _fetching.value = false
        }
    }

    /** IP-geolocate once; a failure with a cached fix still leaves that fix. */
    private suspend fun locate(): GeoPosition? {
        return when (val found = api.locate()) {
            is LocationResult.Found ->
                GeoPosition(found.latitude, found.longitude, found.city)
            else -> {
                AppLog.line("QuickNotch weather locate failed: $found")
                null
            }
        }
    }

    // ── disk cache ───────────────────────────────────────────────────────────

    @Serializable
    private data class CachedWeather(
        val lat: Double,
        val lon: Double,
        val city: String = "",
        val temperature: Double,
        val humidity: Int? = null,
        val code: Int = 0,
        val isDay: Boolean = true,
        val days: List<CachedDay> = emptyList(),
        val fetchedAtMillis: Long = 0L
    )

    @Serializable
    private data class CachedDay(
        val dateIso: String,
        val code: Int = 0,
        val high: Double? = null,
        val low: Double? = null,
        val precipitationProbability: Int? = null,
        val uvIndexMax: Double? = null
    )

    private fun readCache(): CachedWeather? = runCatching {
        if (!cacheFile.isFile) return null
        json.decodeFromString(CachedWeather.serializer(), cacheFile.readText())
    }.getOrNull()

    private fun CachedWeather.toData(): WeatherData = WeatherData(
        now = WeatherNow(temperature, humidity, code, isDay),
        days = days.map {
            WeatherDay(it.dateIso, it.code, it.high, it.low, it.precipitationProbability, it.uvIndexMax)
        },
        fahrenheit = false,
        place = city,
        fetchedAtMillis = fetchedAtMillis
    )

    private fun writeCache(fix: GeoPosition, data: WeatherData) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(
                json.encodeToString(
                    CachedWeather.serializer(),
                    CachedWeather(
                        lat = fix.latitude,
                        lon = fix.longitude,
                        city = fix.city,
                        temperature = data.now.temperature,
                        humidity = data.now.humidity,
                        code = data.now.code,
                        isDay = data.now.isDay,
                        days = data.days.map {
                            CachedDay(
                                it.dateIso, it.code, it.high, it.low,
                                it.precipitationProbability, it.uvIndexMax
                            )
                        },
                        fetchedAtMillis = data.fetchedAtMillis
                    )
                )
            )
        }
    }
}
