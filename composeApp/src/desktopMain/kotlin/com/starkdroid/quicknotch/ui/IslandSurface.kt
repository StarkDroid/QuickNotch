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
 * rendering does no shader work beyond a flat paint plus (for [NotchSurface.BLUR])
 * the pre-blurred frost tile from [NoiseGenerator] — the whole island is
 * repainted by the software renderer every animation frame, so nothing here may
 * depend on size or time.
 *
 * [NotchSurface.BLUR] fakes glassmorphism: a translucent base lets the real
 * desktop bleed through the transparent window while a gaussian-blurred cloud
 * texture frosts it. A true live blur of the pixels behind a borderless window
 * is unavailable from Compose without a native screen-capture loop.
 */
internal object IslandSurface {

    data class Style(
        val fill: Brush,
        val frost: Boolean,
        val border: Color,
        val borderWidth: Float
    )

    private val styles: Map<NotchSurface, Style> = NotchSurface.entries.associateWith { surface ->
        when (surface) {
            NotchSurface.SOLID -> Style(
                fill = SolidColor(Color(0xFF000000)),
                frost = false,
                border = Color.White.copy(alpha = 0.12f),
                borderWidth = 0.5f
            )

            NotchSurface.BLUR -> Style(
                fill = Brush.verticalGradient(
                    0f to Color(0xB31B2433),
                    0.5f to Color(0x9A101622),
                    1f to Color(0xA60A0E17)
                ),
                frost = true,
                border = Color.White.copy(alpha = 0.24f),
                borderWidth = 0.75f
            )
        }
    }

    fun styleFor(surface: NotchSurface): Style = styles.getValue(surface)
}

/**
 * Paints the cached surface: fill first, then (glass only) the frosted cloud
 * shader from [NoiseGenerator.getFrostBrush]. The rim is applied separately by
 * callers so each can match its own shape.
 */
internal fun Modifier.islandSurface(
    surface: NotchSurface,
    frost: Brush
): Modifier {
    val style = IslandSurface.styleFor(surface)
    var m = this.background(style.fill)
    if (style.frost) m = m.background(frost)
    return m
}
