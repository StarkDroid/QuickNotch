package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs

/**
 * Pager state shared with the Windows AWT wheel listener, which runs on
 * the render thread. One gesture flips one page and swallows momentum
 * until gestureGapNs.
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
     * Accumulates wheel deltas; one step flips one page, then locks the burst
     * and swallows the tail.
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
