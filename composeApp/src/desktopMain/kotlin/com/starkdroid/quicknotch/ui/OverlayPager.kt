package com.starkdroid.quicknotch.ui

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
 * Gesture model: ONE continuous event stream flips at most ONE page. The
 * burst locks on the first flip, swallows everything that follows (trackpad
 * momentum included), and only releases when the stream has been quiet for
 * [gestureGapNs] — which is exactly when the fingers have left the surface.
 * A new swipe can therefore be switched instantly, and every swipe that
 * actually delivers travel will always page exactly once.
 */
internal class OverlayPager(
    /** Scroll pixels required to flip one page. */
    val stepPx: Float
) {

    /** Index of the page currently on screen; clamped when the page set shrinks. */
    val pageIndex = mutableIntStateOf(0)

    /** Holds Compose's `showDock` so wheel input can be gated on it outside composition. */
    val dockVisible = mutableStateOf(false)

    private var scrollAccum = 0f

    /**
     * Quiet gap after which the previous burst is over and the next swipe
     * (or wheel notch stream) can take effect.
     */
    private val gestureGapNs = 40_000_000L
    private var lastEventNanos = 0L
    private var burstFlipped = false

    /** Current number of widget pages (Apps, Music, …). Synced by the overlay. */
    var pageCount: Int = 1

    /**
     * Feeds one wheel sample (already in screen pixels) into the accumulator.
     * A no-op while the dock is hidden or when there is nothing to page
     * between. Fractional deltas accumulate until one step clears [stepPx];
     * each flip zeroes the travel, locks the burst, and the tail is swallowed.
     */
    fun consumeWheel(deltaPx: Float) {
        if (!dockVisible.value || pageCount <= 1 || deltaPx == 0f) return
        val now = System.nanoTime()
        if (now - lastEventNanos > gestureGapNs) {
            burstFlipped = false
            scrollAccum = 0f
        }
        lastEventNanos = now
        if (burstFlipped) return
        scrollAccum += deltaPx
        if (abs(scrollAccum) >= stepPx) flipOnce()
    }

    private fun flipOnce() {
        val step = if (scrollAccum > 0) 1 else -1
        val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
        scrollAccum = 0f
        if (next != pageIndex.value) {
            pageIndex.value = next
        }
        burstFlipped = true
    }

    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum = 0f
        burstFlipped = false
    }
}
