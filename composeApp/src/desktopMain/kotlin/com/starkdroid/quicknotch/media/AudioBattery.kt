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

/**
 * A connected audio device reporting a battery percentage (0-100).
 *
 * @param isAudio true when the OS data reports the device as part of a
 *   Bluetooth audio stack (Hands-Free / A2DP / AVRCP), which drives which
 *   glyph the level ring shows. Defaults to true because the macOS plist path
 *   has no reliable device-class field and only surfaces battery-carrying
 *   devices anyway.
 */
data class AudioDeviceBattery(
    /** Device name as reported by the OS; null when unknown. */
    val name: String?,
    val levelPercent: Int,
    val isAudio: Boolean = true
)

/** Output of [battery.ps1] (both the fast probe and the slow refresh). */
@Serializable
private data class BatteryProbe(
    @SerialName("present") val present: Boolean = false,
    @SerialName("instance") val instance: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("level") val level: Int? = null,
    @SerialName("audio") val audio: Boolean = false
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
 * Presence on Windows comes from the OS's live Bluetooth connection state
 * ([battery.ps1] mode A: `Windows.Devices.Enumeration` +
 * `BluetoothDevice.ConnectionStatus`), NOT from the battery property — PnP
 * keeps reporting paired devices as present and hands out cached battery
 * values for seconds after the device is gone. The connection probe therefore
 * surfaces a connect or disconnect on the very next poll (≈1 s):
 *
 *  - the 1 s probe asks "is a Bluetooth audio device connected right now"
 *    (a fast WinRT enumeration; the PnP instance walk is skipped entirely
 *    when nothing is connected, so a disconnect is effectively immediate);
 *  - the ~30 s refresh reads that device's battery level with a generous
 *    timeout (the OS may poll the headset over the air for many seconds) and
 *    only publishes while presence still says the device is connected;
 *  - the last-known level is persisted per device instance, so reconnecting
 *    the same device (or restarting the app) restores the chip instantly;
 *  - [poke] forces an immediate probe whenever the notch opens, and a device
 *    that just appeared kicks an immediate refresh so the level shows up
 *    right away.
 *
 * macOS has no public API for this; the Bluetooth preferences plist
 * (~/Library/Preferences/com.apple.Bluetooth.plist) is read with `plutil`,
 * which is fast enough to carry both presence and battery in the same 1 s
 * poll. A failed or misparsed read simply leaves the value null and the UI
 * hides the chip.
 */
internal object AudioBattery {

    private const val PresencePollMs = 1_000L
    private const val LiveProbeTimeoutMs = 8_000L
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
    // it is only replaced by a successful read, and only used while the probe
    // confirms the same instance is still connected.
    @Volatile
    private var activeInstance: String? = null
    @Volatile
    private var cachedInstance: String? = null
    @Volatile
    private var cachedLevel: Int? = null
    @Volatile
    private var cachedName: String? = null

    // Whether the active device is part of a Bluetooth audio stack; picked up
    // from the probe alongside identity so the UI's level-ring glyph matches.
    @Volatile
    private var cachedAudio = false

    // True while the last probe (mode A, real connection state) saw an audio
    // device connected. The slow refresh consults this before publishing — a
    // mode-B snapshot alone cannot be trusted, because the battery property
    // keeps answering for a device that is already unplugged.
    @Volatile
    private var isPresent = false

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

    private fun kickRefresh() {
        val active = scope ?: return
        active.launch { refreshBattery() }
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
            // Spawn failed or was killed: no opinion, keep the current display.
            return
        }
        val line = raw.lineSequence().firstOrNull { it.startsWith("{") } ?: return
        val snap = runCatching {
            batteryJson.decodeFromString(BatteryProbe.serializer(), line)
        }.getOrNull() ?: return

        if (snap.present) {
            val instance = snap.instance ?: return
            val appeared = instance != activeInstance || cachedInstance != instance
            activeInstance = instance
            isPresent = true
            cachedName = snap.name?.takeIf { it.isNotBlank() } ?: cachedName
            cachedAudio = snap.audio
            // A device that just appeared (or one we have no level for yet)
            // kicks an immediate refresh so the percentage shows up quickly.
            if (appeared || cachedLevel == null) kickRefresh()
            val level = if (cachedInstance == instance) cachedLevel else null
            _info.value = if (level != null) AudioDeviceBattery(cachedName, level, cachedAudio) else null
        } else {
            // The WinRT connected set is empty — the device is gone. Hide now;
            // the cached level is kept for an instant restore on reconnect.
            isPresent = false
            activeInstance = null
            _info.value = null
        }
    }

    // ── Refresh (Windows, ~30 s cadence) ─────────────────────────────────────

    private fun refreshBattery() {
        if (!HostPlatform.isWindows) return
        val file = script ?: return
        // Only ever probe a known device instance (mode B); the probe's mode A
        // finds the connected device and drives identity + presence.
        val instance = activeInstance ?: return
        val raw = runProcess(
            listOf(
                "powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath, "-InstanceId", instance
            ),
            RefreshTimeoutMs
        ) ?: return
        val line = raw.lineSequence().firstOrNull { it.startsWith("{") } ?: return
        val snap = runCatching {
            batteryJson.decodeFromString(BatteryProbe.serializer(), line)
        }.getOrNull() ?: return

        // The probe has the final word on presence: mode B's own `present`
        // merely reflects PnP presence, which a paired-but-off device still
        // reports as true — and its battery answer can be a stale cached
        // value. Nothing publishes once presence says the device is gone.
        if (!isPresent || instance != activeInstance) return

        val level = snap.level ?: return // connected but no data yet: keep display
        cachedInstance = instance
        cachedLevel = level
        cachedName = snap.name?.takeIf { it.isNotBlank() } ?: cachedName
        cachedAudio = snap.audio
        persistCache(instance, cachedName, level)
        _info.value = AudioDeviceBattery(cachedName, level, cachedAudio)
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
        runCatching {
            val file = cacheFile() ?: return
            if (!file.isFile) return
            val parsed = batteryJson.decodeFromString(BatteryCache.serializer(), file.readText())
            cachedInstance = parsed.instance
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