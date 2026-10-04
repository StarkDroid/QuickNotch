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
 *
 * One wheel notch maps to one page step; fractional deltas (precision
 * trackpads, free-spin wheels) accumulate via [scrollAccum] until a step
 * clears [stepPx].
 *
 * One swipe gesture flips at most ONE page, no matter how hard it is: the
 * first threshold crossing in a burst takes the page and the rest of the
 * burst (trackpad momentum, a fast multi-notch fling) is swallowed. The next
 * page waits for the next gesture — a fresh burst after a short quiet gap.
 * Without this, a hard swipe crosses the threshold several times and blows
 * straight past the middle pages, which then only ever flash by mid-flight
 * instead of settling. A burst ends after [GESTURE_GAP_NS] of quiet; discrete
 * wheel ticks carry pauses and keep working tick-per-page.
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
     * Quiet gap that ends a swipe burst and re-arms the next flip. Trackpad
     * momentum arrives as a continuous stream with tiny gaps, so it never
     * re-arms mid-flight; deliberate wheel ticks carry human pauses and do.
     */
    private val gestureGapNs = 180_000_000L
    private var lastEventNanos = 0L
    private var burstFlipped = false

    /** Current number of widget pages (Apps, Music, …). Synced by the overlay. */
    var pageCount: Int = 1

    /**
     * Feeds one wheel sample (already in screen pixels) into the accumulator
     * and flips at most one page per swipe burst. A no-op while the dock is
     * hidden or when there is nothing to page between. Uses a monotonic clock
     * for the burst gap so wall-clock jumps never glue two gestures together.
     */
    fun consumeWheel(deltaPx: Float) {
        if (!dockVisible.value || pageCount <= 1 || deltaPx == 0f) return
        val now = System.nanoTime()
        // A quiet gap ends the previous burst and re-arms paging; travel left
        // over from it never carries into the next page.
        if (now - lastEventNanos > gestureGapNs) {
            burstFlipped = false
            scrollAccum.value = 0f
        }
        lastEventNanos = now
        // Already flipped inside this burst: swallow the momentum tail so a
        // hard swipe can never blow past the neighbouring page.
        if (!burstFlipped) {
            scrollAccum.value += deltaPx
            if (abs(scrollAccum.value) >= stepPx) {
                val step = if (scrollAccum.value > 0) 1 else -1
                val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
                scrollAccum.value = 0f
                if (next != pageIndex.value) {
                    pageIndex.value = next
                    burstFlipped = true
                }
            }
        }
    }

    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum.value = 0f
        burstFlipped = false
    }
}
