package com.starkdroid.quicknotch.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.IntSize
import java.awt.MouseInfo
import java.awt.Point


/**
 * The hover area is a sibling above the dock.
 * sharePointerInputWithSiblings keeps hover events flowing while the dock
 * keeps scroll and clicks.
 */
internal class SharingHoverNode(
    var onHoverChanged: (Boolean) -> Unit
) : Modifier.Node(), PointerInputModifierNode {

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        when (event.type) {
            PointerEventType.Enter -> onHoverChanged(true)
            PointerEventType.Exit -> onHoverChanged(false)
            else -> Unit
        }
    }

    override fun onCancelPointerInput() {
        // Callback-only hover tracking holds no gesture state to reset.
    }

    override fun sharePointerInputWithSiblings(): Boolean = true
}

internal class SharingHoverElement(
    private val onHoverChanged: (Boolean) -> Unit
) : ModifierNodeElement<SharingHoverNode>() {
    override fun create(): SharingHoverNode = SharingHoverNode(onHoverChanged)
    override fun update(node: SharingHoverNode) {
        node.onHoverChanged = onHoverChanged
    }

    override fun equals(other: Any?): Boolean =
        other is SharingHoverElement && other.onHoverChanged == onHoverChanged

    override fun hashCode(): Int = onHoverChanged.hashCode()
}

internal fun Modifier.sharingHover(onHoverChanged: (Boolean) -> Unit): Modifier =
    this.then(SharingHoverElement(onHoverChanged))

/**
 * True while the real cursor is inside the island. Collapse is gated on
 * this because resize events flap the hover region; awtDrift corrects
 * AWT's stale origin above the menu bar.
 */
internal fun cursorOverNotch(coordinates: LayoutCoordinates?, awtDrift: Point?): Boolean {
    val coords = coordinates ?: return true
    if (!coords.isAttached) return true
    val pointer = MouseInfo.getPointerInfo()?.location ?: return true
    val topLeft = coords.localToScreen(Offset.Zero)
    val size = coords.size
    val bottomRight = coords.localToScreen(
        Offset(size.width.toFloat(), size.height.toFloat())
    )
    val drift = awtDrift
    val shiftedTopLeft = if (drift != null) Offset(topLeft.x + drift.x, topLeft.y + drift.y) else topLeft
    val shiftedBottomRight = if (drift != null) Offset(bottomRight.x + drift.x, bottomRight.y + drift.y) else bottomRight
    return Rect(shiftedTopLeft, shiftedBottomRight).contains(
        Offset(pointer.x.toFloat(), pointer.y.toFloat())
    )
}
