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
import java.io.File
import java.util.concurrent.TimeUnit

/** Output of `system-battery.ps1` / `pmset -g batt` (macOS). */
@Serializable
private data class SystemBatteryProbe(
    @SerialName("present") val present: Boolean = false,
    @SerialName("level") val level: Int? = null,
    @SerialName("charging") val charging: Boolean = false
)

private val systemBatteryJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * The machine's own battery (a laptop): level 0-100 and whether the OS is
 * actively charging it. Null while no battery exists (desktops), so the
 * collapsed-pill indicator only ever appears on machines that can actually
 * drain.
 *
 * Polling lives in the companion: it runs independently of [AudioBattery]
 * (same ring design, different data source). Windows reads `Win32_Battery`
 * through [system-battery.ps1]; macOS parses `pmset -g batt`. A failed or
 * misparsed read leaves the last value alone, so the indicator only ever
 * shows real data.
 */
data class SystemBattery(
    val levelPercent: Int,
    val charging: Boolean
) {
    companion object {

        private const val PollMs = 5_000L
        private const val ProbeTimeoutMs = 8_000L
        private const val MacProbeTimeoutMs = 3_000L

        private val _state = MutableStateFlow<SystemBattery?>(null)
        val state: StateFlow<SystemBattery?> = _state.asStateFlow()

        /**
         * Whether the machine actually has a battery (a laptop vs. a desktop).
         * Stays false until the first successful probe, then tracks presence;
         * it gates the settings toggle so desktops never offer a dead control.
         */
        private val _hasBattery = MutableStateFlow(false)
        val hasBattery: StateFlow<Boolean> = _hasBattery.asStateFlow()

        @Volatile
        private var script: File? = null

        private var scope: CoroutineScope? = null
        private var pollJob: Job? = null

        fun start() {
            if (HostPlatform.isWindows) {
                script = NativeResources.extract("windows/system-battery.ps1", ".ps1")
            }
            stop()
            val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = active
            pollJob = active.launch {
                while (isActive) {
                    probe()
                    delay(PollMs)
                }
            }
        }

        fun stop() {
            pollJob?.cancel()
            pollJob = null
            scope?.cancel()
            scope = null
        }

        /** Forces an immediate probe so the ring is fresh the moment the notch opens. */
        fun poke() {
            val active = scope ?: return
            active.launch { probe() }
        }

        private fun probe() {
            if (HostPlatform.isWindows) probeWindows() else probeMac()
        }

        private fun probeWindows() {
            val file = script ?: return
            val raw = runProcess(
                listOf(
                    "powershell.exe",
                    "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", file.absolutePath
                ),
                ProbeTimeoutMs
            ) ?: return
            val line = raw.lineSequence().firstOrNull { it.startsWith("{") } ?: return
            val snap = runCatching {
                systemBatteryJson.decodeFromString(SystemBatteryProbe.serializer(), line)
            }.getOrNull() ?: return
            _hasBattery.value = snap.present
            if (!snap.present) {
                _state.value = null
                return
            }
            val level = snap.level ?: return
            _state.value = SystemBattery(level.coerceIn(0, 100), snap.charging)
        }

        // ── macOS (`pmset -g batt`) ────────────────────────────────────────────
        //
        //   Now drawing from 'AC Power'
        //    -InternalBattery-0 (id=123)	87%; charging; 0:00 remaining present: true

        private fun probeMac() {
            val raw = runProcess(listOf("pmset", "-g", "batt"), MacProbeTimeoutMs) ?: return
            val parsed = parseMacBattery(raw)
            _hasBattery.value = parsed != null
            _state.value = parsed
        }

        private fun parseMacBattery(raw: String): SystemBattery? {
            val lines = raw.lineSequence().toList()
            // "No batteries available." carries no percentage.
            if (lines.none { it.contains('%') }) return null
            val onAc = lines.any { it.contains("AC Power") }
            val batteryLine = lines.firstOrNull { it.contains('%') } ?: return null
            val level = Regex("(\\d+)%").find(batteryLine)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            if (level !in 0..100) return null
            // "charged" (full, on AC) is not charging; "charging"/"finishing charge" is.
            val charging = onAc && (batteryLine.contains("charging") || batteryLine.contains("finishing charge"))
            return SystemBattery(level, charging)
        }

        private fun runProcess(command: List<String>, timeoutMs: Long): String? {
            return runCatching {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                    return null
                }
                process.inputStream.bufferedReader().readText().trim()
            }.getOrNull()
        }
    }
}