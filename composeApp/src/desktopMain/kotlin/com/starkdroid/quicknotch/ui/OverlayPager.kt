package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs
import kotlin.math.sign

/**
 * Page-switch state for the expanded dock's widget pager.
 * One wheel notch/trackpad flick produces exactly one page flip.
 */
internal class OverlayPager(
    /** Scroll pixels required to flip one page. */
    val stepPx: Float
) {

    val pageIndex = mutableIntStateOf(0)
    val dockVisible = mutableStateOf(false)

    private val scrollAccum = mutableFloatStateOf(0f)

    private val gestureGapNs = 100_000_000L
    private val shoveDelayNs = 120_000_000L
    private var lastEventNanos = 0L
    private var lastFlipNanos = 0L
    private var lastDeltaPx = 0f
    private var burstFlipped = false
    private var lastFlipDir = 0

    var pageCount: Int = 1

    fun consumeWheel(deltaPx: Float) {
        if (!dockVisible.value || pageCount <= 1 || deltaPx == 0f) return
        val now = System.nanoTime()

        if (now - lastEventNanos > gestureGapNs) {
            burstFlipped = false
            scrollAccum.value = 0f
        }
        lastEventNanos = now

        if (burstFlipped) {
            val settled = now - lastFlipNanos > shoveDelayNs
            val fullStep = abs(deltaPx) >= stepPx
            val risingEdge = lastDeltaPx != 0f && abs(deltaPx) > 2 * abs(lastDeltaPx)
            val reversed = lastFlipDir != 0 && sign(deltaPx).toInt() != lastFlipDir && abs(deltaPx) > stepPx / 4
            lastDeltaPx = deltaPx
            if (settled && (fullStep || risingEdge || reversed)) {
                burstFlipped = false
                scrollAccum.value = 0f
            } else {
                return
            }
        } else {
            lastDeltaPx = deltaPx
        }

        scrollAccum.value += deltaPx
        if (abs(scrollAccum.value) >= stepPx) {
            val step = if (scrollAccum.value > 0) 1 else -1
            val next = (pageIndex.value + step).coerceIn(0, pageCount - 1)
            scrollAccum.value = 0f
            if (next != pageIndex.value) {
                pageIndex.value = next
                lastFlipDir = step
            }
            burstFlipped = true
            lastFlipNanos = now
        }
    }

    fun resetAccum() {
        scrollAccum.value = 0f
        burstFlipped = false
    }
}
