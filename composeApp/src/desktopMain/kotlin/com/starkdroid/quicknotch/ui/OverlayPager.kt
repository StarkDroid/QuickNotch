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
 * The burst re-arms on a genuine *re-flick*: the previous impulse's
 * envelope first decays to near the floor, then a new strong event arrives —
 * the finger lifted and a new swipe began. A continuous keep-finger-down
 * drag never produces this shape (its tail is a single decaying wave), so
 * one held swipe = one page, while repeated flicks after a brief settle
 * flip at once. Direction reversals count as re-flicks immediately.
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

    // Per-burst tail envelope used to recognize a *new* trackpad flick after
    // the previous impulse has settled. decayPeak tracks the strongest event
    // of the post-flip tail; postFlipSawDecay marks that the tail had time to
    // drop to a fraction of that peak (quiet floor), and postFlipTrough keeps
    // the event where. A new flick is recognized when the instantaneous
    // magnitude rises back over the trough significantly.
    private var decayPeak = 0f
    private var postFlipSawDecay = false
    private var postFlipTroughSeen = false
    private var postFlipTrough = 0f

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
        val mag = abs(deltaPx)
        val streamPaused = now - lastEventNanos > gestureGapNs

        if (burstFlipped) {
            // After the flip, a new page can only come from a genuinely *fresh*
            // impulse. On a trackpad that means the previous impulse's envelope
            // first decayed back toward stillness and then rose again — exactly
            // the quality of a second, separate flick. A single slow
            // keep-finger-down drag keeps delivering the tail's same-direction
            // travel and therefore never re-arms.
            val decayedToQuiet = postFlipSawDecay && mag <= decayPeak * 0.25f
            if (decayedToQuiet) {
                postFlipTroughSeen = true
                postFlipTrough = mag
            }
            val freshImpulse = postFlipTroughSeen &&
                mag >= maxOf(postFlipTrough * 3f, decayPeak * 0.5f)
            val signFlip = lastEventSign != 0 && sign != lastEventSign && postFlipTroughSeen
            val restartGesture = streamPaused || freshImpulse || signFlip
            if (restartGesture) {
                burstFlipped = false
                postFlipSawDecay = false
                postFlipTroughSeen = false
                decayPeak = 0f
                postFlipTrough = 0f
                scrollAccum.value = if (streamPaused) 0f else deltaPx
                if (abs(scrollAccum.value) >= stepPx) flipOnce()
            } else {
                if (mag > decayPeak) decayPeak = mag
                if (mag <= 0.25f * decayPeak.coerceAtLeast(stepPx)) postFlipSawDecay = true
            }
        } else {
            if (streamPaused) scrollAccum.value = 0f
            scrollAccum.value += deltaPx
            if (abs(scrollAccum.value) >= stepPx) flipOnce()
        }

        lastEventNanos = now
        lastEventSign = sign
    }

    private fun flipOnce() {
        val step = if (scrollAccum.value > 0) 1 else -1
        val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
        scrollAccum.value = 0f
        if (next != pageIndex.value) {
            pageIndex.value = next
        }
        burstFlipped = true
        postFlipSawDecay = false
        postFlipTroughSeen = false
        decayPeak = 0f
        postFlipTrough = 0f
    }


    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum.value = 0f
        burstFlipped = false
        lastEventSign = 0
    }
}
