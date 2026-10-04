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
 *   glyph the level ring shows. Decided from real device metadata on both
 *   platforms; defaults to true as the least-bad fallback for unknown devices.
 */
data class AudioDeviceBattery(
    /** Device name as reported by the OS; null when unknown. */
    val name: String?,
    val levelPercent: Int,
    val isAudio: Boolean = true,
    /**
     * How many Bluetooth devices the OS reports as connected right now,
     * including the one this ring reads its level from. The ring only shows a
     * count badge above 1, so a single connected device looks exactly as it did
     * before this field existed.
     */
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
 * Battery percentage of the connected Bluetooth audio device for the music
 * widget (quickview chip + expanded page).
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
 * macOS has no public API for this; `system_profiler SPBluetoothDataType`
 * reports the connected device's battery and services (≈100 ms, so it carries
 * both presence and battery in the poll — at a slower 10 s cadence to keep the
 * laptop quiet, with an instant re-probe when the notch opens/music goes live).
 * The old Bluetooth preferences plist
 * (~/Library/Preferences/com.apple.Bluetooth.plist) stopped carrying a
 * DeviceCache on modern macOS, so it is kept only as a fallback for older
 * systems. A failed or misparsed read simply leaves the value null and the UI
 * hides the chip.
 */
internal object AudioBattery {

    private const val PresencePollMs = 1_000L
    // system_profiler is more expensive than the Windows WinRT probe, so the
    // macOS cadence is slower; a notch-open or music-live poke still probes
    // immediately, so connect/disconnect reflects right away on expand.
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
    /** Connected-device count from the last presence probe (Windows). */
    private var cachedCount = 1

    // True while the last probe (mode A, real connection state) saw an audio
    // device connected. The slow refresh consults this before publishing — a
    // mode-B snapshot alone cannot be trusted, because the battery property
    // keeps answering for a device that is already unplugged.
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
                    // While no level has ever been read the refresh runs on a
                    // short retry cadence; once known, a slow ~30 s tick keeps
                    // the chip honest without hammering the OS.
                    delay(if (cachedLevel == null) BatteryFirstRetryMs else BatteryRefreshMs)
                }
            }
        } else {
            // Events bridge connect/disconnect instantly; the 10 s poll above
            // becomes a backstop for OSes that never post a notification.
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
     * Forces an immediate connectivity probe (and a battery re-read when a
     * device is present), so the chip right after the notch opens is fresh.
     */
    /**
     * Force an immediate re-probe (notch opened, etc).
     *
     * Coalesced, because a probe is a *process launch*: on macOS it is
     * `system_profiler`, which costs ~150ms of system-wide CPU. Without the
     * guard, every track start (musicLive flipping) fired one exactly when the
     * user was least likely to want a system-wide stall, and rapid open/close
     * cycles stacked more on top. Probes that started within
     * [pokeCooldownMs] are dropped instead: the 10s poll and the event-driven
     * connect/disconnect bridge already keep the chip honest, so a poke more
     * often than that buys nothing a user could see.
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
            if (snap.count != null) cachedCount = snap.count
            _info.value = if (level != null) {
                AudioDeviceBattery(cachedName, level, cachedAudio, cachedCount)
            } else {
                null
            }
        } else {
            // The WinRT connected set is empty — the device is gone. Hide now;
            // the cached level is kept for an instant restore on reconnect.
            isPresent = false
            activeInstance = null
            cachedCount = 1
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
        _info.value = AudioDeviceBattery(cachedName, level, cachedAudio, cachedCount)
    }

    // ── macOS (10 s cadence, system_profiler carries presence + battery) ────

    private fun probeMac(): Boolean {
        // system_profiler carries presence + battery in one fast call.
        //
        // macOS, however, keeps recent earphones listed as Connected (an
        // audio-capable BLE link) even while audio is actually playing through
        // other speakers — so a headset ring must additionally be checked
        // against the *active output*, or it reads as a phantom device. Plist
        // remains a fallback for older systems that still write a DeviceCache;
        // every path that finds nothing local ends by clearing _info so a
        // disconnected ring can never linger. Returns true when a ring was
        // published (the Bluetooth-change watcher uses that to decide whether
        // to re-probe once the audio routing settles).
        val raw = runProcess(listOf("system_profiler", "SPBluetoothDataType"), MacProbeTimeoutMs)
        val candidate = raw?.let { parseSystemProfiler(it) }
        if (candidate != null && candidate.name != null) {
            // A probe failure leaves the last value alone (no opinion); a clear
            // "the default output is something else" hides the ring right away.
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

    // ── macOS Bluetooth-change watcher ────────────────────────────────────────

    /**
     * Supervises the `btwatch-bridge` helper: it watches CoreAudio default-
     * output changes plus bluetoothd connect/disconnect notifications and
     * prints one `AUDIO`/`BT` marker per event burst, letting the battery
     * poller probe the stack the instant a device connects instead of up to
     * the 10 s backstop cadence later. Restarts the process if it dies (like
     * the now-playing bridge). Absent on non-mac builds — the poller simply
     * keeps its slow cadence.
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
     * A device connected/disconnected at the Bluetooth level. Probe now; if
     * the immediate snapshot still found nothing routed, re-probe ~1.2 s later
     * so macOS can finish registering the device as the audio output (that
     * beats the previous "up to 10 s later"). Cheap: one or two system_profiler
     * calls per actual Bluetooth connect/disconnect.
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
     * Name of the device the OS currently routes audio to, or null when the
     * probe failed outright (caller keeps the last value — no opinion). An
     * empty string means the probe ran but no Default Output Device line
     * exists, i.e. audio is definitely not routed to a named wireless device.
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

    // system_profiler flags a headset as audio through its service list
    // (A2DP is the music path; HFP/AVRCP/AACP tag the voice/control stacks) or
    // through a headphone-style Minor Type line. Both are honoured so realme
    // earbuds (usually only "Battery Level: nn%") and Apple headphones (with a
    // full Services line) resolve to the headphone-glyph ring.
    private val macAudioServices = setOf("HFP", "AVRCP", "A2DP", "AACP")
    private val macAudioMinorTypes = setOf("Headphones", "Headset", "Earbuds", "Audio")

    /**
     * Parses `system_profiler SPBluetoothDataType`: walks the Connected:
     * section, and picks the battery of the first device the OS classifies as
     * audio. A headset exposing separate earbuds reports its Left/Right
     * average; a device with a single percentage uses that; the case level is
     * the last resort. `isAudio` is decided from the Services/Minor Type lines,
     * not assumed — BLE-only idle links (AirPods in their case, Find My) never
     * publish because their Services line carries no audio tags.
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

        // system_profiler indents the section header N spaces, device names N+4
        // and attributes N+8 — names carry the device label (no value), while
        // attributes are always "Key: value" lines, so indentation alone splits
        // them robustly regardless of the header's own indent.
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

        // Only a device the OS classifies as audio is published: macOS also
        // lists idle BLE links (e.g. AirPods in their case nearby, Find My) as
        // "Connected" with a Services line that is BLE-only, and surfacing
        // those as a bluetooth ring reads as a ghost device to the user.
        val candidates = devices.mapNotNull { device ->
            macDeviceLevel(device)?.let { (level, audio) -> Triple(device, level, audio) }
        }
        val pick = candidates.firstOrNull { it.third } ?: return null
        val name = pick.first.name.takeIf { it.isNotBlank() }
        // Every entry under "Connected:" counts, audio or not: the badge answers
        // "how many Bluetooth devices do I have around right now?", which is a
        // question about the OS's connected set rather than about the one
        // device whose battery this ring happens to show.
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
     * Name of the device marked as the current output in `SPAudioDataType`.
     * Its section heading ("Devices:") sits at N spaces and device names at N+4
     * with "Key: value" attrs deeper — the same indent shape the Bluetooth
     * parser relies on, so the same regexes apply.
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
