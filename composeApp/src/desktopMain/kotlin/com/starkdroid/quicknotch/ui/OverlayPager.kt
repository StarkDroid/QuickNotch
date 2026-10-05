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
 * actually sees WM_MOUSEWHEEL. That listener is attached in `Main` (where the
 * AWT `window` is in scope) and drives this state from the AWT event thread —
 * the same thread Compose Desktop renders on, so no synchronization is needed.
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
     */
    private val gestureGapNs = 50_000_000L
    private var lastEventNanos = 0L
    private var burstFlipped = false
    private var lastFlipSign = 0

    /** Current number of widget pages (Apps, Music, …). Synced by the overlay. */
    var pageCount: Int = 1

    /**
     * Feeds one wheel sample (already in screen pixels) into the accumulator.
     * One flick flips at most one page; rapid back-and-forth is allowed by
     * re-arming on direction reversal.
     */
    fun consumeWheel(deltaPx: Float) {
        if (!dockVisible.value || pageCount <= 1 || deltaPx == 0f) return
        val now = System.nanoTime()
        val deltaSign = if (deltaPx > 0f) 1 else -1

        if (burstFlipped && lastFlipSign != 0 && deltaSign != lastFlipSign) {
            burstFlipped = false
            scrollAccum.value = 0f
        } else if (now - lastEventNanos > gestureGapNs) {
            burstFlipped = false
            scrollAccum.value = 0f
        }
        lastEventNanos = now
        if (burstFlipped) return
        scrollAccum.value += deltaPx
        if (abs(scrollAccum.value) >= stepPx) flipOnce()
    }

    /** Advances one page in the accumulated direction and locks the burst. */
    private fun flipOnce() {
        val step = if (scrollAccum.value > 0) 1 else -1
        val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
        scrollAccum.value = 0f
        if (next != pageIndex.value) {
            pageIndex.value = next
        }
        burstFlipped = true
        lastFlipSign = step
    }

    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum.value = 0f
        burstFlipped = false
    }
}
