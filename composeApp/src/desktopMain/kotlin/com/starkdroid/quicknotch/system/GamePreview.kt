package com.starkdroid.quicknotch.system

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
import kotlin.random.Random

/**
 * TEMPORARY — a macOS-only preview of the gaming HUD.
 *
 * The real HUD appears only while a fullscreen game is detected, and its frame
 * rate comes from PresentMon on Windows. That makes the layout impossible to
 * look at on a Mac, so this publishes a synthetic session plus a randomised
 * frame rate that the overlay treats exactly like a real one. CPU, GPU, ping
 * and download are the genuine readings from [SystemMetrics], because the HUD
 * is fed by that poller anyway (see `gamingActive` in `main.kt`).
 *
 * It is deliberately one file with a handful of marked call sites: to go back
 * to the real, Windows-only behaviour, delete this file and the `GamePreview`
 * references in `main.kt` and `FloatingNotchOverlay.kt`. On Windows everything
 * here is already inert — [enabled] is false and both flows stay null/none.
 */
internal object GamePreview {

    /** macOS only: Windows keeps the real detector and PresentMon. */
    val enabled: Boolean = HostPlatform.isMac

    private val _session = MutableStateFlow<GameSession?>(
        if (enabled) GameSession(name = "Preview") else null
    )
    val session: StateFlow<GameSession?> = _session.asStateFlow()

    private val _fps = MutableStateFlow<Int?>(null)
    val fps: StateFlow<Int?> = _fps.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    /**
     * Starts the fake frame clock. A plausible range around a 120 Hz display,
     * re-rolled every [FpsIntervalMs] so the strip looks alive while the rest of
     * the page settles.
     */
    fun start() {
        if (!enabled || job != null) return
        val active = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = active
        job = active.launch {
            while (isActive) {
                _fps.value = Random.nextInt(MinFps, MaxFps + 1)
                delay(FpsIntervalMs)
            }
        }
    }

    fun stop() {
        job = null
        scope?.cancel()
        scope = null
        _fps.value = null
    }

    private const val MinFps = 96
    private const val MaxFps = 168
    private const val FpsIntervalMs = 1_200L
}
