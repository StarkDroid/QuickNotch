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

/** A connected audio device reporting a battery percentage (0-100); [isAudio] picks the ring glyph. */
data class AudioDeviceBattery(
    /** Device name as reported by the OS; null when unknown. */
    val name: String?,
    val levelPercent: Int,
    val isAudio: Boolean = true,
    /** Bluetooth devices the OS reports connected right now, including this one. */
    val connectedCount: Int = 1
)

/** Output of [battery.ps1] (both the fast probe and the slow refresh). */
@Serializable
private data class BatteryProbe(
    @SerialName("present") val present: Boolean = false,
    @SerialName("instance") val instance: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("level") val level: Int? = null,
    @SerialName("audio") val audio: Boolean = false,
    /** Connected-device count (mode A only); null when the script didn't say. */
    @SerialName("count") val count: Int? = null
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
 * Battery percentage of the connected Bluetooth audio device for the quickview chip.
 * Windows must read presence from live WinRT connection state; the PnP battery property caches a dead device.
 */
internal object AudioBattery {

    private const val PresencePollMs = 1_000L
    // system_profiler costs more than the WinRT probe, so macOS polls slower.
    private const val MacPresencePollMs = 10_000L
    private const val LiveProbeTimeoutMs = 8_000L
    private const val BatteryRefreshMs = 30_000L
    private const val BatteryFirstRetryMs = 5_000L
    private const val RefreshTimeoutMs = 40_000L
    private const val MacProbeTimeoutMs = 6_000L

    private val _info = MutableStateFlow<AudioDeviceBattery?>(null)
    val info: StateFlow<AudioDeviceBattery?> = _info.asStateFlow()

    @Volatile
    private var script: File? = null

    // Level is kept across disconnects so reconnecting the same device restores instantly.
    @Volatile
    private var activeInstance: String? = null
    @Volatile
    private var cachedInstance: String? = null
    @Volatile
    private var cachedLevel: Int? = null
    @Volatile
    private var cachedName: String? = null

    // Whether the active device is part of a Bluetooth audio stack; drives the ring glyph.
    @Volatile
    private var cachedAudio = false
    /** Connected-device count from the last presence probe (Windows). */
    private var cachedCount = 1

    // Last mode-A probe saw an audio device connected; mode B's battery answer outlives a disconnect.
    @Volatile
    private var isPresent = false

    private var scope: CoroutineScope? = null
    private var presenceJob: Job? = null
    private var batteryJob: Job? = null

    /** The macOS `btwatch-bridge` process watching bluetoothd notifications. */
    @Volatile
    private var macWatch: Process? = null

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
                delay(if (HostPlatform.isWindows) PresencePollMs else MacPresencePollMs)
            }
        }
        if (HostPlatform.isWindows) {
            batteryJob = active.launch {
                while (isActive) {
                    refreshBattery()
                    // Retry fast until a level is known, then settle on the slow refresh cadence.
                    delay(if (cachedLevel == null) BatteryFirstRetryMs else BatteryRefreshMs)
                }
            }
        } else {
            // Events bridge connect/disconnect; the poll is only a backstop.
            startMacWatch()
        }
    }

    fun stop() {
        macWatch?.destroy()
        macWatch = null
        presenceJob?.cancel()
        presenceJob = null
        batteryJob?.cancel()
        batteryJob = null
        scope?.cancel()
        scope = null
    }

    /**
     * Forces an immediate re-probe (notch opened, music went live).
     * Coalesced on a 4 s cooldown: each probe is a process launch, ~150 ms of system CPU on macOS.
     */
    fun poke() {
        val active = scope ?: return
        val now = System.nanoTime()
        synchronized(lock = pokeLock) {
            val last = lastPokeNanos
            if (last != 0L && (now - last) / 1_000_000L < pokeCooldownMs) return
            lastPokeNanos = now
        }
        active.launch {
            probePresence()
            refreshBattery()
        }
    }

    private val pokeLock = Any()
    private var lastPokeNanos = 0L
    private const val pokeCooldownMs = 4_000L

    private fun kickRefresh() {
        val active = scope ?: return
        active.launch { refreshBattery() }
    }

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
            // A device that just appeared kicks an immediate refresh so the percentage shows up.
            if (appeared || cachedLevel == null) kickRefresh()
            val level = if (cachedInstance == instance) cachedLevel else null
            if (snap.count != null) cachedCount = snap.count
            _info.value = if (level != null) {
                AudioDeviceBattery(cachedName, level, cachedAudio, cachedCount)
            } else {
                null
            }
        } else {
            // WinRT connected set is empty: hide now, keep the cached level for an instant restore.
            isPresent = false
            activeInstance = null
            cachedCount = 1
            _info.value = null
        }
    }

    private fun refreshBattery() {
        if (!HostPlatform.isWindows) return
        val file = script ?: return
        // Only a known instance is refreshed; the probe's mode A owns identity and presence.
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

        // Mode B's own `present` only reflects PnP presence and its battery can be cached, so
        // nothing publishes once mode A says the device is gone.
        if (!isPresent || instance != activeInstance) return

        val level = snap.level ?: return // connected but no data yet: keep display
        cachedInstance = instance
        cachedLevel = level
        cachedName = snap.name?.takeIf { it.isNotBlank() } ?: cachedName
        cachedAudio = snap.audio
        persistCache(instance, cachedName, level)
        _info.value = AudioDeviceBattery(cachedName, level, cachedAudio, cachedCount)
    }

    private fun probeMac(): Boolean {
        // macOS keeps recently used earphones listed as Connected even when audio plays through
        // other speakers, so the candidate must match the active output. Returns true when published.
        val raw = runProcess(listOf("system_profiler", "SPBluetoothDataType"), MacProbeTimeoutMs)
        val candidate = raw?.let { parseSystemProfiler(it) }
        if (candidate != null && candidate.name != null) {
            // A probe failure leaves the last value alone; a mismatch hides the ring right away.
            val activeOutput = macActiveOutputName() ?: return false
            if (activeOutput.isNotEmpty() && macNamesMatch(activeOutput, candidate.name!!)) {
                _info.value = candidate
                return true
            }
            _info.value = null
            return false
        }
        val home = System.getProperty("user.home")
        val plist = home?.let { File(it, "Library/Preferences/com.apple.Bluetooth.plist") }
        if (plist == null || !plist.isFile) {
            _info.value = null
            return false
        }
        val plistRaw = runProcess(
            listOf("plutil", "-convert", "json", "-o", "-", plist.absolutePath),
            MacProbeTimeoutMs
        ) ?: run {
            _info.value = null
            return false
        }
        val parsed = parseMacPlist(plistRaw) // null when nothing / parse fails
        _info.value = parsed
        return parsed != null
    }

    /**
     * `btwatch-bridge` watcher: an AUDIO/BT marker probes the stack at once instead of waiting
     * up to the 10 s backstop. Restarts if the process dies; absent on non-mac builds.
     */
    private fun startMacWatch() {
        val binary = NativeResources.extract("macos/btwatch-bridge", "")
        if (binary == null || !binary.canExecute()) return
        val active = scope ?: return
        active.launch {
            while (isActive) {
                runCatching {
                    val process = ProcessBuilder(binary.absolutePath)
                        .redirectErrorStream(false)
                        .start()
                    macWatch = process
                    process.inputStream.bufferedReader().use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (line == "AUDIO" || line == "BT") onMacWatchEvent()
                        }
                    }
                    process.waitFor()
                }
                macWatch = null
                delay(2_000)
            }
        }
    }

    /**
     * A connect/disconnect that is not yet the audio output gets one re-probe ~1.2 s later,
     * after macOS finishes registering it.
     */
    private fun onMacWatchEvent() {
        val active = scope ?: return
        active.launch {
            if (!probeMac()) {
                delay(1_200)
                probeMac()
            }
        }
    }

    /**
     * Device the OS routes audio to, or null when the probe failed (keep the last value).
     * Empty string means the probe ran and nothing is routed to a named device.
     */
    private fun macActiveOutputName(): String? {
        val audioRaw = runProcess(listOf("system_profiler", "SPAudioDataType"), MacProbeTimeoutMs) ?: return null
        return macDefaultOutputDevice(audioRaw) ?: ""
    }

    /** Loose name equality (case-insensitive, either name contains the other). */
    private fun macNamesMatch(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        return x == y || x.contains(y) || y.contains(x)
    }

    // system_profiler marks a headset as audio via its service list (A2DP music, HFP/AVRCP/AACP
    // voice) or a headphone-style Minor Type; both are honoured.
    private val macAudioServices = setOf("HFP", "AVRCP", "A2DP", "AACP")
    private val macAudioMinorTypes = setOf("Headphones", "Headset", "Earbuds", "Audio")

    /**
     * Parses `system_profiler SPBluetoothDataType`: first connected device the OS classifies as
     * audio. Earbuds report Left/Right (averaged), case level is the last resort.
     */
    private fun parseSystemProfiler(raw: String): AudioDeviceBattery? {
        val header = raw.indexOf("Connected:")
        if (header < 0) return null
        val sectionStart = header + "Connected:".length
        val notConnected = raw.indexOf("Not Connected:", sectionStart)
        val section = raw.substring(
            sectionStart,
            if (notConnected > sectionStart) notConnected else raw.length
        )

        // Device names are indented N+4 with no value, attributes N+8 as "Key: value",
        // so indentation alone splits them regardless of the header's own indent.
        val headerIndent = countIndent(raw, header)
        val nameRegex = Regex("^\\s{${headerIndent + 4}}\\S.*:$")
        val attrRegex = Regex("^\\s+([^:]+):\\s+(.+)$")

        var current: MacDevice? = null
        val devices = mutableListOf<MacDevice>()
        for (line in section.lineSequence()) {
            if (line.isBlank()) continue
            if (nameRegex.matches(line)) {
                current = MacDevice(line.dropWhile { it == ' ' }.dropLast(1))
                devices.add(current)
            } else {
                val match = attrRegex.find(line) ?: continue
                current?.attrs[match.groupValues[1].trim()] = match.groupValues[2].trim()
            }
        }

        // Only a device classified as audio is published; idle BLE links read as a ghost device.
        val candidates = devices.mapNotNull { device ->
            macDeviceLevel(device)?.let { (level, audio) -> Triple(device, level, audio) }
        }
        val pick = candidates.firstOrNull { it.third } ?: return null
        val name = pick.first.name.takeIf { it.isNotBlank() }
        // The badge counts every entry under Connected, audio or not: it answers "how many
        // Bluetooth devices are around", not which one this ring reads.
        return AudioDeviceBattery(name, pick.second, pick.third, devices.size)
    }

    private class MacDevice(val name: String, val attrs: MutableMap<String, String> = mutableMapOf())

    /** Extracts [level, isAudio] from a device block, or null when no battery. */
    private fun macDeviceLevel(device: MacDevice): Pair<Int, Boolean>? {
        val attrs = device.attrs
        val services = attrs["Services"].orEmpty()
        val minor = attrs["Minor Type"].orEmpty()
        val audio = macAudioServices.any { services.contains(it) } || minor.trim() in macAudioMinorTypes
        val left = attrs["Left Battery Level"]?.extractPercent()
        val right = attrs["Right Battery Level"]?.extractPercent()
        val single = attrs["Battery Level"]?.extractPercent()
        val case = attrs["Case Battery Level"]?.extractPercent()
        val level = when {
            left != null && right != null -> (left + right + 1) / 2
            single != null -> single
            left != null -> left
            right != null -> right
            case != null -> case
            else -> null
        } ?: return null
        return level to audio
    }

    /**
     * Parses the current output device from SPAudioDataType, the same indent
     * shape as the Bluetooth parser.
     */
    private fun macDefaultOutputDevice(raw: String): String? {
        val devices = raw.indexOf("Devices:")
        if (devices < 0) return null
        val headerIndent = countIndent(raw, devices)
        val nameRegex = Regex("^\\s{${headerIndent + 4}}\\S.*:$")
        val attrRegex = Regex("^\\s+([^:]+):\\s+(.+)$")

        var current: String? = null
        for (line in raw.substring(devices).lineSequence()) {
            if (line.isBlank()) continue
            if (nameRegex.matches(line)) {
                current = line.dropWhile { it == ' ' }.dropLast(1)
            } else {
                val match = attrRegex.find(line) ?: continue
                if (
                    match.groupValues[1].trim() == "Default Output Device" &&
                    match.groupValues[2].trim() == "Yes"
                ) {
                    return current
                }
            }
        }
        return null
    }

    /** "93%" → 93; null when the value carries no number. */
    private fun String.extractPercent(): Int? =
        Regex("\\d+").find(this)?.value?.toIntOrNull()

    private fun countIndent(line: String): Int {
        var i = 0
        while (i < line.length && line[i] == ' ') i++
        return i
    }

    private fun countIndent(raw: String, index: Int): Int {
        val lineStart = raw.lastIndexOf('\n', index).let { if (it < 0) 0 else it + 1 }
        val lineEnd = raw.indexOf('\n', index).let { if (it < 0) raw.length else it }
        return countIndent(raw.substring(lineStart, lineEnd))
    }

    /**
     * First connected device with a numeric BatteryPercent key in the
     * Bluetooth DeviceCache plist.
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
