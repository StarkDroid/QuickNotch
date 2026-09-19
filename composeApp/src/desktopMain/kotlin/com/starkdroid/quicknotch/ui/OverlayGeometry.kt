package com.starkdroid.quicknotch.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle

val NotchPosition.isVertical: Boolean
    get() = this == NotchPosition.LEFT_CENTER || this == NotchPosition.RIGHT_CENTER

val NotchPosition.isBottom: Boolean
    get() = this == NotchPosition.BOTTOM_CENTER ||
        this == NotchPosition.BOTTOM_LEFT ||
        this == NotchPosition.BOTTOM_RIGHT

val NotchPosition.isTop: Boolean
    get() = this == NotchPosition.TOP_CENTER ||
        this == NotchPosition.TOP_LEFT ||
        this == NotchPosition.TOP_RIGHT

fun overlayWindowSize(
    config: NotchConfig,
    favoriteCount: Int,
    expanded: Boolean
): DpSize {
    val along = (favoriteCount * (config.baseIconSizeDp + 14) + 88).coerceIn(300, 900)
    // Extra depth leaves room for dock magnification without clipping.
    val depthExpanded = (config.expandedNotchHeightDp + 22).dp
    val depthCollapsed = (config.collapsedPillHeightDp + 6).dp
    val alongCollapsed = (config.collapsedPillWidthDp + 6).dp
    return if (config.position.isVertical) {
        if (expanded) DpSize(depthExpanded, along.dp) else DpSize(depthCollapsed, alongCollapsed)
    } else {
        if (expanded) DpSize(along.dp, depthExpanded) else DpSize(alongCollapsed, depthCollapsed)
    }
}

fun overlayScreenLocation(
    position: NotchPosition,
    windowWidthPx: Int,
    windowHeightPx: Int,
    screen: Rectangle,
    insets: Insets,
    hugMenuBar: Boolean
): Point {
    val topInset = if (hugMenuBar && position.isTop) 0 else insets.top
    val left = screen.x + insets.left
    val top = screen.y + topInset
    val right = screen.x + screen.width - insets.right
    val bottom = screen.y + screen.height - insets.bottom
    val availableWidth = (right - left).coerceAtLeast(windowWidthPx)
    val availableHeight = (bottom - top).coerceAtLeast(windowHeightPx)

    val xCenter = left + (availableWidth - windowWidthPx) / 2
    val yCenter = top + (availableHeight - windowHeightPx) / 2
    val cornerPad = 16

    return when (position) {
        NotchPosition.TOP_CENTER -> Point(xCenter, top)
        NotchPosition.BOTTOM_CENTER -> Point(xCenter, bottom - windowHeightPx)
        NotchPosition.TOP_LEFT -> Point(left + cornerPad, top)
        NotchPosition.TOP_RIGHT -> Point(right - windowWidthPx - cornerPad, top)
        NotchPosition.BOTTOM_LEFT -> Point(left + cornerPad, bottom - windowHeightPx)
        NotchPosition.BOTTOM_RIGHT -> Point(right - windowWidthPx - cornerPad, bottom - windowHeightPx)
        NotchPosition.LEFT_CENTER -> Point(left, yCenter)
        NotchPosition.RIGHT_CENTER -> Point(right - windowWidthPx, yCenter)
    }
}

fun snapToNotchPosition(
    centerX: Int,
    centerY: Int,
    screen: Rectangle
): NotchPosition {
    val distTop = (centerY - screen.y).coerceAtLeast(0)
    val distBottom = (screen.y + screen.height - centerY).coerceAtLeast(0)
    val distLeft = (centerX - screen.x).coerceAtLeast(0)
    val distRight = (screen.x + screen.width - centerX).coerceAtLeast(0)
    val minDist = minOf(distTop, distBottom, distLeft, distRight)
    val leftBand = screen.x + screen.width * 0.30f
    val rightBand = screen.x + screen.width * 0.70f
    val topBand = screen.y + screen.height * 0.30f
    val bottomBand = screen.y + screen.height * 0.70f

    return when (minDist) {
        distTop -> when {
            centerX < leftBand -> NotchPosition.TOP_LEFT
            centerX > rightBand -> NotchPosition.TOP_RIGHT
            else -> NotchPosition.TOP_CENTER
        }
        distBottom -> when {
            centerX < leftBand -> NotchPosition.BOTTOM_LEFT
            centerX > rightBand -> NotchPosition.BOTTOM_RIGHT
            else -> NotchPosition.BOTTOM_CENTER
        }
        distLeft -> when {
            centerY < topBand -> NotchPosition.TOP_LEFT
            centerY > bottomBand -> NotchPosition.BOTTOM_LEFT
            else -> NotchPosition.LEFT_CENTER
        }
        else -> when {
            centerY < topBand -> NotchPosition.TOP_RIGHT
            centerY > bottomBand -> NotchPosition.BOTTOM_RIGHT
            else -> NotchPosition.RIGHT_CENTER
        }
    }
}
