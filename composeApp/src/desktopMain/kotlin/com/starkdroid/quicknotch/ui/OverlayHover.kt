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

// ── Hover input sharing ────────────────────────────────────────────────────

/**
 * Hover tracking lives on a small transparent sibling Box that sits on top of
 * the dock. On macOS the dock Box carries a scroll handler and is therefore
 * pointer-eligible, so a plain [Modifier.onPointerEvent] hover area would be
 * shadowed by it: when siblings overlap, hit testing only adds the highest-z
 * eligible one, and the dock is drawn above the hover area.
 *
 * This node opts into [sharePointerInputWithSiblings], so once the hover area
 * is hit the hit test keeps walking down through the dock subtree and both
 * stay on the pointer path — hover Enter/Exit still fires while the dock keeps
 * its scroll handling and icon clicks. See the sibling ordering in
 * [FloatingNotchOverlay] for why the hover area has to be on top for this to
 * work.
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
 * True while the cursor is inside the notch's current hover footprint
 * (island + glow, in screen coordinates). Collapse is gated on this rather
 * than on an Exit event alone because undecorated layered overlay windows
 * re-state their hover region when they resize under a stationary cursor on
 * macOS and Windows alike — with media ticking its progress under the pointer,
 * trusting the raw event made the notch flap open/closed while hovering.
 *
 * [awtDrift] is the live difference between the window's real CG origin and
 * AWT's cached `locationOnScreen` (see `MacAppKit.screenOriginDelta`): Compose's
 * `localToScreen` rides the stale AWT position, which the above-menu-bar
 * placement invalidates by exactly the menu-bar height. Null means no drift.
 *
 * Unknown footprint or pointer position errs toward keeping the notch open.
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
