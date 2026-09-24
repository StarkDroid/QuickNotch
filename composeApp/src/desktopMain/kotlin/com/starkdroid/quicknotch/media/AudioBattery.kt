package com.starkdroid.quicknotch.media

import com.starkdroid.quicknotch.datasource.HostPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/** A connected audio device reporting a battery percentage (0-100). */
data class AudioDeviceBattery(
    /** Device name as reported by the OS; null when unknown. */
    val name: String?,
    val levelPercent: Int
)

/** Output of [battery.ps1] (both the fast probe and the slow refresh). */
@Serializable
private data class BatteryProbe(
    @SerialName("present") val present: Boolean = false,
    @SerialName("instance") val instance: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("level") val level: Int? = null
)

/** Last-known level persisted per device instance (Windows). */
@Serializable
private data class BatteryCache(
    @SerialName("instance") val instance: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("level") val level: Int? = null
)

private val batteryJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Battery percentage of the connected Bluetooth audio device for the music
 * widget (collapsed pill chip + expanded page).
 *
 * Reading a device's battery on Windows can stall for many seconds while the
 * OS polls the headset over the air, and PnP reports every paired radio
 * instance as present, so there is no cheap "is it connected" signal — the
 * battery property is the only way to tell that a device is actually
 * answering. The poller therefore uses the read itself adaptively:
 *
 *  - the 1 s probe runs [battery.ps1] with a short timeout. A probe killed at
 *    the timeout means the OS is still busy talking to a live device, so the
 *    last-known level keeps showing; a probe that answers fast with no data
 *    means the device is gone or silent, and the chip hides immediately —
 *    disconnects surface within a couple of seconds;
 *  - the ~30 s refresh re-reads the level with a generous timeout so the chip
 *    stays honest, and the last-known level is persisted per device instance
 *    so reconnecting the same device (or restarting the app) restores it
 *    instantly instead of waiting out the first slow read;
 *  - [poke] forces an immediate probe whenever the notch opens, so the chip
 *    right after opening is fresh.
 *
 * macOS has no public API for this; the Bluetooth preferences plist
 * (~/Library/Preferences/com.apple.Bluetooth.plist) is read with `plutil`,
 * which is fast enough to carry both presence and battery in the same 1 s
 * poll. A failed or misparsed read simply leaves the value null and the UI
 * hides the chip.
 */
internal object AudioBattery {

    private const val PresencePollMs = 1_000L
    private const val LiveProbeTimeoutMs = 2_500L
    private const val BatteryRefreshMs = 30_000L
    private const val BatteryFirstRetryMs = 5_000L
    private const val RefreshTimeoutMs = 40_000L
    private const val MacProbeTimeoutMs = 3_000L

    private val _info = MutableStateFlow<AudioDeviceBattery?>(null)
    val info: StateFlow<AudioDeviceBattery?> = _info.asStateFlow()

    @Volatile
    private var script: File? = null

    // Identity + level currently believed. The level is deliberately kept
    // across disconnects so reconnecting the same device restores instantly;
    // it is only replaced by a successful read.
    @Volatile
    private var activeInstance: String? = null
    @Volatile
    private var cachedLevel: Int? = null
    @Volatile
    private var cachedName: String? = null

    // Restored from disk on start (Windows) so a restart with the same device
    // connected shows the level immediately instead of a slow first read.
    @Volatile
    private var restoredInstance: String? = null

    private var scope: CoroutineScope? = null
    private var presenceJob: Job? = null
    private var batteryJob: Job? = null

    fun start() {
        if (HostPlatform.isWindows) {
            script = NativeResources.extract("windows/battery.ps1", ".ps1")
            loadPersistedCache()
        }
        stop()
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        presenceJob = active.launch {
            while (isActive) {
                probePresence()
                delay(PresencePollMs)
            }
        }
        if (HostPlatform.isWindows) {
            batteryJob = active.launch {
                while (isActive) {
                    refreshBattery()
                    // While no level has ever been read the refresh runs on a
                    // short retry cadence; once known, a slow ~30 s tick keeps
                    // the chip honest without hammering the OS.
                    delay(if (cachedLevel == null) BatteryFirstRetryMs else BatteryRefreshMs)
                }
            }
        }
    }

    fun stop() {
        presenceJob?.cancel()
        presenceJob = null
        batteryJob?.cancel()
        batteryJob = null
        scope?.cancel()
        scope = null
    }

    /**
     * Forces an immediate connectivity probe (and a battery re-read when a
     * device is present), so the chip right after the notch opens is fresh.
     */
    fun poke() {
        val active = scope ?: return
        active.launch {
            probePresence()
            refreshBattery()
        }
    }

    // ── Probe (1 s cadence) ──────────────────────────────────────────────────

    private fun probePresence() {
        if (!HostPlatform.isWindows) {
            probeMac()
            return
        }
        val file = script ?: return
        val raw = runProcess(
            listOf(
                "powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath
            ),
            LiveProbeTimeoutMs
        )
        if (raw == null) {
            // Killed at the cap: the OS is still polling the headset over the
            // air, so the device is reachable. Keep showing the last-known
            // level; on a fresh session adopt the persisted identity.
            val level = cachedLevel
            if (level != null) {
                if (activeInstance == null) activeInstance = restoredInstance
                if (activeInstance != null) {
                    _info.value = AudioDeviceBattery(cachedName, level)
                }
            }
            return
        }
        val line = raw.lineSequence().firstOrNull { it.startsWith("{") } ?: return
        val snap = runCatching {
            batteryJson.decodeFromString(BatteryProbe.serializer(), line)
        }.getOrNull() ?: return

        when {
            !snap.present -> {
                // Device deregistered entirely.
                activeInstance = null
                _info.value = null
            }
            snap.level != null -> {
                val instance = snap.instance ?: return
                activeInstance = instance
                cachedLevel = snap.level
                cachedName = snap.name?.takeIf { it.isNotBlank() } ?: cachedName
                persistCache(instance, cachedName, snap.level)
                _info.value = AudioDeviceBattery(cachedName, snap.level)
            }
            else -> {
                // Fast "no data": device is in the tree but not answering
                // (disconnected, or reports no battery at all). Hide the chip;
                // the cached level is kept for an instant restore on reconnect.
                if (snap.instance != null) activeInstance = snap.instance
                _info.value = null
            }
        }
    }

    // ── Refresh (Windows, ~30 s cadence) ─────────────────────────────────────

    private fun refreshBattery() {
        if (!HostPlatform.isWindows) return
        val file = script ?: return
        // Unknown identity: let the script find the device itself (mode A).
        // Known identity: probe exactly that instance (mode B).
        val instance = activeInstance
        val command = if (instance != null) {
            listOf(
                "powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath, "-InstanceId", instance
            )
        } else {
            listOf(
                "powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath
            )
        }
        val raw = runProcess(command, RefreshTimeoutMs) ?: return
        val line = raw.lineSequence().firstOrNull { it.startsWith("{") } ?: return
        val snap = runCatching {
            batteryJson.decodeFromString(BatteryProbe.serializer(), line)
        }.getOrNull() ?: return

        when {
            !snap.present -> {
                if (instance == null || instance == activeInstance) {
                    activeInstance = null
                    _info.value = null
                }
            }
            snap.level != null -> {
                val resolved = snap.instance ?: instance ?: return
                if (instance == null || resolved == activeInstance) {
                    activeInstance = resolved
                    cachedLevel = snap.level
                    cachedName = snap.name?.takeIf { it.isNotBlank() } ?: cachedName
                    persistCache(resolved, cachedName, snap.level)
                    _info.value = AudioDeviceBattery(cachedName, snap.level)
                }
            }
            else -> {
                if (instance == null || instance == activeInstance) _info.value = null
            }
        }
    }

    // ── macOS (1 s cadence, plist carries presence + battery) ────────────────

    private fun probeMac() {
        val home = System.getProperty("user.home") ?: return
        val plist = File(home, "Library/Preferences/com.apple.Bluetooth.plist")
        if (!plist.isFile) return
        val raw = runProcess(
            listOf("plutil", "-convert", "json", "-o", "-", plist.absolutePath),
            MacProbeTimeoutMs
        ) ?: return
        _info.value = parseMacPlist(raw)
    }

    /**
     * Walks the Bluetooth preferences plist's DeviceCache for the first
     * connected device reporting a numeric `BatteryPercent*` key. The plist's
     * connection flags are written by the OS the moment devices pair/unpair,
     * so connect/disconnect reflects on the very next poll.
     */
    private fun parseMacPlist(raw: String): AudioDeviceBattery? {
        val root = runCatching { batteryJson.parseToJsonElement(raw) }.getOrNull() ?: return null
        if (root !is JsonObject) return null
        val cache = root["DeviceCache"] as? JsonObject ?: return null
        for ((_, entry) in cache) {
            val obj = entry as? JsonObject ?: continue
            val connected = obj["Connected"]
            if (connected != null) {
                val on = when (connected) {
                    is JsonPrimitive -> when {
                        connected.booleanOrNull == true -> true
                        connected.booleanOrNull == false -> false
                        else -> (connected.contentOrNull?.toIntOrNull() ?: 0) > 0
                    }
                    else -> false
                }
                if (!on) continue
            }
            val name = (obj["Name"] as? JsonPrimitive)?.contentOrNull
            var best = 0
            for ((key, value) in obj) {
                if (key.startsWith("BatteryPercent")) {
                    val v = (value as? JsonPrimitive)?.intOrNull ?: continue
                    if (v in 0..100 && v > best) best = v
                }
            }
            if (best > 0) {
                return AudioDeviceBattery(name?.takeIf { it.isNotBlank() }, best)
            }
        }
        return null
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun runProcess(command: List<String>, timeoutMs: Long): String? {
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            // Wait before reading so a genuinely hung child is killed instead
            // of blocking the poller forever (a battery read can stall for
            // many seconds while the OS talks to the headset).
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                return null
            }
            process.inputStream.bufferedReader().readText().trim()
        }.getOrNull()
    }

    private fun cacheFile(): File? {
        return runCatching {
            val dir = if (HostPlatform.isMac) {
                File(System.getProperty("user.home"), "Library/Application Support/QuickNotch")
            } else {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "QuickNotch")
                else File(System.getProperty("user.home"), ".quicknotch")
            }
            if (!dir.exists()) dir.mkdirs()
            File(dir, "battery.json")
        }.getOrNull()
    }

    private fun loadPersistedCache() {
        restoredInstance = null
        runCatching {
            val file = cacheFile() ?: return
            if (!file.isFile) return
            val parsed = batteryJson.decodeFromString(BatteryCache.serializer(), file.readText())
            restoredInstance = parsed.instance
            cachedLevel = parsed.level
            cachedName = parsed.name?.takeIf { it.isNotBlank() }
        }
    }

    private fun persistCache(instance: String, name: String?, level: Int) {
        runCatching {
            val file = cacheFile() ?: return
            file.writeText(
                batteryJson.encodeToString(BatteryCache.serializer(), BatteryCache(instance, name, level))
            )
        }
    }
}