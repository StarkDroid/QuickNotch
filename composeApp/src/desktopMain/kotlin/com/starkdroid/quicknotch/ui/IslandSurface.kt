package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.starkdroid.quicknotch.model.NotchSurface

/**
 * The body material for the notch island. Both entries resolve to brushes built
 * once and cached in [styles], so switching never reallocates and steady-state
 * rendering does no shader work beyond one or two flat paints — the island is
 * repainted by the software renderer every animation frame, so nothing here may
 * depend on size, time or per-pixel texture.
 *
 * [NotchSurface.TRANSLUCENT] is deliberately texture-free: a mottled or grainy
 * overlay reads as paper, not glass. It instead composes two perfectly smooth
 * gradients — a translucent tinted body that lets the real desktop bleed
 * through the transparent window, plus a soft diagonal light sheen — which is
 * what reads visually as glass. A literal gaussian blur of the pixels behind a
 * borderless transparent window is not reachable from Compose without a native
 * screen-capture loop (which would also recapture the island itself).
 */
internal object IslandSurface {

    data class Style(
        val fill: Brush,
        val sheen: Brush?,
        val border: Color,
        val borderWidth: Float
    )

    private val styles: Map<NotchSurface, Style> = NotchSurface.entries.associateWith { surface ->
        when (surface) {
            NotchSurface.SOLID -> Style(
                fill = SolidColor(Color(0xFF000000)),
                sheen = null,
                border = Color.White.copy(alpha = 0.12f),
                borderWidth = 0.5f
            )

            NotchSurface.TRANSLUCENT -> Style(
                fill = Brush.verticalGradient(
                    0f to Color(0xC01C2637),
                    0.55f to Color(0xAB101723),
                    1f to Color(0xB80A0D15)
                ),
                sheen = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.35f to Color.White.copy(alpha = 0.025f),
                    0.7f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.05f)
                ),
                border = Color.White.copy(alpha = 0.28f),
                borderWidth = 0.75f
            )
        }
    }

    fun styleFor(surface: NotchSurface): Style = styles.getValue(surface)
}

/**
 * Paints the cached surface: translucent body first, then the light sheen for
 * glass. The rim is applied separately by callers so each can match its own
 * shape.
 */
internal fun Modifier.islandSurface(surface: NotchSurface): Modifier {
    val style = IslandSurface.styleFor(surface)
    var m = this.background(style.fill)
    if (style.sheen != null) m = m.background(style.sheen)
    return m
}
