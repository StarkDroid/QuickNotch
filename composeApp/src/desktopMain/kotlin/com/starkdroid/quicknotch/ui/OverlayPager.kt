package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs

/**
 * Page-switch state for the expanded dock's widget pager, shared between the
 * overlay (which renders the pages and expands/collapses the dock) and — on
 * Windows — an AWT-level wheel listener attached to the overlay window.
 *
 * The overlay window is unfocused and transparent, so it never receives wheel
 * input through Compose's pointer pipeline on Windows; AWT is the layer that
 * actually sees WM_MOUSEWHEEL. That listener is attached in `Main` and drives
 * this state from the AWT event thread — the same thread Compose Desktop
 * renders on, so no synchronization is needed.
 *
 * Gesture model: ONE continuous stream of wheel/trackpad events is one
 * gesture. It can flip at most one page; after that all its remaining travel
 * (trackpad momentum tail included) is swallowed. The next page flip can only
 * happen once the stream goes quiet for [gestureGapNs] and the user delivers
 * a fresh gesture. No mid-stream re-arm exists on purpose — any signal we
 * could use (large event, rising edge, directory reversal) also shows up
 * inside a single hard flick, so honouring any reintroduces the overshoot
 * where one swipe blows past the middle pages.
 */
internal class OverlayPager(
    /** Scroll pixels required to flip one page. */
    val stepPx: Float
) {

    /** Index of the page currently on screen; clamped when the page set shrinks. */
    val pageIndex = mutableIntStateOf(0)

    /** Holds Compose's `showDock` so wheel input can be gated on it outside composition. */
    val dockVisible = mutableStateOf(false)

    private val scrollAccum = mutableFloatStateOf(0f)

    /**
     * Quiet gap that ends a swipe burst and re-arms the next flip. Uses a
     * monotonic clock so wall-clock jumps never glue two gestures together.
     * Trackpad emails within a flick/momentum arrive with single-digit-millisecond
     * gaps, so 60ms is reliably one gesture; a deliberate new flick is defined
     * by at least that silence.
     */
    private val gestureGapNs = 60_000_000L
    private var lastEventNanos = 0L
    private var burstFlipped = false

    /** Current number of widget pages (Apps, Music, …). Synced by the overlay. */
    var pageCount: Int = 1

    /**
     * Feeds one wheel sample (already in screen pixels) into the accumulator.
     * A no-op while the dock is hidden or when there is nothing to page
     * between. Fractional deltas (precision trackpads, free-spin wheels)
     * accumulate until one step clears [stepPx]; the flip zeroes the travel
     * and locks the burst.
     */
    fun consumeWheel(deltaPx: Float) {
        if (!dockVisible.value || pageCount <= 1 || deltaPx == 0f) return
        val now = System.nanoTime()
        if (now - lastEventNanos > gestureGapNs) {
            burstFlipped = false
            scrollAccum.value = 0f
        }
        lastEventNanos = now
        if (burstFlipped) return
        scrollAccum.value += deltaPx
        if (abs(scrollAccum.value) >= stepPx) {
            val step = if (scrollAccum.value > 0) 1 else -1
            val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
            scrollAccum.value = 0f
            if (next != pageIndex.value) {
                pageIndex.value = next
            }
            burstFlipped = true
        }
    }

    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum.value = 0f
        burstFlipped = false
    }
}
