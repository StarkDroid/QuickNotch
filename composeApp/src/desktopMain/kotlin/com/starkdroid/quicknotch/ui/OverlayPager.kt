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

    /** Current number of widget pages (Apps, Music, …). Synced by the overlay. */
    var pageCount: Int = 1

    /**
     * Feeds one wheel sample (already in screen pixels) into the accumulator
     * and flips pages once it crosses [stepPx]. A no-op while the dock is
     * hidden or when there is nothing to page between.
     */
    fun consumeWheel(deltaPx: Float) {
        if (!dockVisible.value || pageCount <= 1) return
        if (deltaPx == 0f) return
        scrollAccum.value += deltaPx
        while (abs(scrollAccum.value) >= stepPx) {
            val step = if (scrollAccum.value > 0) 1 else -1
            scrollAccum.value -= step * stepPx
            val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
            if (next != pageIndex.value) {
                pageIndex.value = next
                scrollAccum.value = 0f
            }
        }
    }

    /** Resets accumulated wheel travel, e.g. after a page change via UI. */
    fun resetAccum() {
        scrollAccum.value = 0f
    }
}