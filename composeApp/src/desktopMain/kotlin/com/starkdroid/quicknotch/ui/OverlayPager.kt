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
 * Gesture model: ONE continuous scroll impulse (trackpad flick + its momentum
 * tail, or one AWT wheel tick with |rotation|≥1) flips at most ONE page.
 * The burst re-arms immediately on any of:
 *  1. a fresh page-sized impulse (|deltaPx| ≥ [stepPx]) — the user's next
 *     flick in the same or opposite direction, which the decaying momentum
 *     of the first flick never reproduces on its own;
 *  2. an explicit direction reversal — an immediate flip of sign means a
 *     deliberately new gesture in the opposite direction;
 *  3. a quiet gap of [gestureGapNs] with no scroll events at all.
 * There is no artificial lockout, so repeated flicks in a row respond at
 * once, while a single swipe can never run away across several pages:
 * once the first page-sized push has consumed the impulse, the remaining
 * decaying tail contains no further full-page push.
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
     * Quiet gap after which the current burst is considered over and a
     * guaranteed-fresh gesture may start. Trackpad impulses deliver events
     * continuously (~16 ms cadence), so 60 ms of true silence is unambiguous.
     */
    private val gestureGapNs = 60_000_000L
    private var lastEventNanos = 0L
    private var burstFlipped = false
    private var lastEventSign = 0

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
        val sign = if (deltaPx > 0f) 1 else -1

        if (burstFlipped) {
            val reversal = lastEventSign != 0 && sign != lastEventSign && abs(deltaPx) >= stepPx / 4
            val freshFullPageImpulse = abs(deltaPx) >= stepPx
            val streamPaused = now - lastEventNanos > gestureGapNs
            if (reversal || freshFullPageImpulse || streamPaused) {
                // Deliberately new gesture: this event starts the new
                // accumulator instead of joining the stale tail.
                burstFlipped = false
                scrollAccum.value = deltaPx
            } else {
                lastEventNanos = now
                return
            }
        }

        if (now - lastEventNanos > gestureGapNs) scrollAccum.value = 0f

        if (now - lastEventNanos > gestureGapNs) {
            scrollAccum.value = 0f
        }
        lastEventNanos = now
        lastEventSign = sign
        scrollAccum.value += deltaPx
        if (abs(scrollAccum.value) >= stepPx) flipOnce()
    }

    private fun flipOnce() {
        val step = if (scrollAccum.value > 0) 1 else -1
        val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
        scrollAccum.value = 0f
        if (next != pageIndex.value) {
            pageIndex.value = next
        }
        burstFlipped = true
    }

    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum.value = 0f
        burstFlipped = false
        lastEventSign = 0
    }
}
