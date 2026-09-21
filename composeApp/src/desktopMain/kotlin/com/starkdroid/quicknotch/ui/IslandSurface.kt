package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.starkdroid.quicknotch.model.NotchSurface

/**
 * The body material for the notch island. Every surface is resolved to a
 * pre-built [Brush] + hairline border and cached in [styles], so switching
 * styles allocates once and steady-state rendering never rebuilds a shader —
 * important because the software renderer redraws the island every animation
 * frame. "Glass" is faked with translucency, a top-down sheen and a faint
 * grain layer rather than a live blur of the desktop behind the window, which
 * Compose cannot sample for a borderless transparent window without a costly
 * native capture loop.
 */
internal object IslandSurface {

    data class Style(
        val fill: Brush,
        val border: Color,
        val borderWidth: Float,
        val grain: Boolean
    )

    private val styles: Map<NotchSurface, Style> = NotchSurface.entries.associateWith { surface ->
        when (surface) {
            NotchSurface.SOLID -> Style(
                fill = SolidColor(Color(0xFF0A0A0A)),
                border = Color.White.copy(alpha = 0.14f),
                borderWidth = 0.5f,
                grain = false
            )

            NotchSurface.AMOLED -> Style(
                fill = SolidColor(Color(0xFF000000)),
                border = Color.White.copy(alpha = 0.08f),
                borderWidth = 0.5f,
                grain = false
            )

            NotchSurface.GLASS -> Style(
                fill = Brush.verticalGradient(
                    0f to Color(0xD01B2331),
                    0.5f to Color(0xC4111621),
                    1f to Color(0xCC0B0E16)
                ),
                border = Color.White.copy(alpha = 0.22f),
                borderWidth = 0.75f,
                grain = true
            )

            NotchSurface.LIQUID_GLASS -> Style(
                fill = Brush.verticalGradient(
                    0f to Color(0xC8253350),
                    0.45f to Color(0xBA121A29),
                    1f to Color(0xC20E131F)
                ),
                border = Color.White.copy(alpha = 0.34f),
                borderWidth = 1f,
                grain = true
            )
        }
    }

    fun styleFor(surface: NotchSurface): Style = styles.getValue(surface)
}

/**
 * Paints a cached [IslandSurface] fill (plus optional grain) into the island.
 * A no-allocation extension usable from both the overlay and the settings
 * preview; the border is applied separately so callers can match their shape.
 */
internal fun Modifier.islandSurface(
    surface: NotchSurface,
    noise: Brush
): Modifier {
    val style = IslandSurface.styleFor(surface)
    var m = this.background(style.fill)
    if (style.grain) m = m.background(noise)
    return m
}