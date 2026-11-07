package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.starkdroid.quicknotch.datasource.BlurRadiusMax
import com.starkdroid.quicknotch.datasource.BlurRadiusMin
import com.starkdroid.quicknotch.model.NotchSurface

/**
 * Island materials, brushes cached and size-independent because the
 * software renderer repaints every frame. TRANSLUCENT and SYSTEM_ACCENT
 * are smooth gradients, never textures; real blur comes from the window.
 */
internal object IslandSurface {

    data class Style(
        val fill: Brush,
        val sheen: Brush?,
        val border: Color,
        val borderWidth: Float
    )

    private val glassSheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.10f),
        0.35f to Color.White.copy(alpha = 0.025f),
        0.7f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.05f)
    )

    private val styles: Map<NotchSurface, Style> = mapOf(
        NotchSurface.SOLID to Style(
            fill = SolidColor(Color(0xFF000000)),
            sheen = null,
            border = Color.White.copy(alpha = 0.12f),
            borderWidth = 0.5f
        ),
        NotchSurface.TRANSLUCENT to Style(
            fill = Brush.verticalGradient(
                0f to Color(0xC01C2637),
                0.55f to Color(0xAB101723),
                1f to Color(0xB80A0D15)
            ),
            sheen = glassSheen,
            border = Color.White.copy(alpha = 0.28f),
            borderWidth = 0.75f
        )
    )

    /**
     * Dark scrim over the native blur for contrast, with the sheen that
     * sells the frost; the slider drives its depth.
     */
    private fun blurScrimFor(level: Int): Style {
        val clamped = level.coerceIn(BlurRadiusMin, BlurRadiusMax)
        val alpha = (ScrimBaseAlpha + clamped * ScrimAlphaPerStep).coerceIn(0f, 0.6f)
        return Style(
            fill = SolidColor(Color.Black.copy(alpha = alpha)),
            sheen = blurSheen,
            // No rim in blurred mode: the frost's own edge already delimits the
            // island against the desktop, and a white hairline drawn over it
            // reads as a bright border around the blur.
            border = Color.Transparent,
            borderWidth = 0f
        )
    }

    // Scrim ramp for the blurred surface: the native frost blurs without
    // adding light, so the scrim alpha carries the intensity slider end to
    // end (near-clear glass at 0, deep frost at the max) while staying light
    // enough that the frost texture survives it.
    private const val ScrimBaseAlpha = 0.04f
    private const val ScrimAlphaPerStep = 0.006f

    /**
     * Brighter sheen for the blurred surface, which has no bright body of
     * its own.
     */
    private val blurSheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.16f),
        0.35f to Color.White.copy(alpha = 0.05f),
        0.7f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.07f)
    )

    // The accent style is a function of the OS accent, so it can't live in the
    // static map above; it is rebuilt only when the accent value changes (a
    // rare, user-driven event) and cached per value so steady-state rendering
    // never reallocates brushes between polls.
    @Volatile private var accented: Style? = null
    @Volatile private var accentedFor: Color? = null

    /**
     * Gaming transparency: no body, rim or shadow, only the HUD glyphs. Not
     * a NotchSurface entry because it is not a user material.
     */
    private val gameTransparent = Style(
        fill = SolidColor(Color.Transparent),
        sheen = null,
        border = Color.Transparent,
        borderWidth = 0f
    )

    fun styleFor(
        surface: NotchSurface,
        accent: Color?,
        blurred: Boolean = false,
        blurLevel: Int = BlurRadiusMax,
        transparentInGame: Boolean = false
    ): Style =
        if (transparentInGame) {
            gameTransparent
        } else if (blurred) {
            blurScrimFor(blurLevel)
        } else if (surface == NotchSurface.SYSTEM_ACCENT) {
            styleForAccent(accent)
        } else {
            styles.getValue(surface)
        }

    private fun styleForAccent(accent: Color?): Style {
        if (accent == null) return styles.getValue(NotchSurface.TRANSLUCENT)
        accented?.let { cached ->
            if (accentedFor == accent) return cached
        }
        return buildAccented(accent).also {
            accented = it
            accentedFor = accent
        }
    }

    /**
     * Translucent-glass body with the desktop accent blended in. The tint is
     * deliberate but restrained (roughly 15% of the full accent on the body) so
     * the notch stays glassy rather than reading as a solid colour swatch.
     */
    private fun buildAccented(accent: Color): Style {
        val accentRim = accent.copy(alpha = 0.55f)
        return Style(
            fill = Brush.verticalGradient(
                0f to lerp(Color(0xC01C2637), accent, 0.16f),
                0.55f to lerp(Color(0xAB101723), accent, 0.13f),
                1f to lerp(Color(0xB80A0D15), accent, 0.10f)
            ),
            sheen = Brush.linearGradient(
                0f to accent.copy(alpha = 0.20f),
                0.35f to Color.White.copy(alpha = 0.025f),
                0.7f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.05f)
            ),
            border = accentRim,
            borderWidth = 0.75f
        )
    }
}

/**
 * Paints the cached surface: translucent body first, then the light sheen for
 * glass. The rim is applied separately by callers so each can match its own
 * shape.
 */
internal fun Modifier.islandSurface(
    surface: NotchSurface,
    accent: Color? = null,
    blurred: Boolean = false,
    blurLevel: Int = BlurRadiusMax,
    transparentInGame: Boolean = false
): Modifier {
    val style = IslandSurface.styleFor(surface, accent, blurred, blurLevel, transparentInGame)
    var m = this.background(style.fill)
    if (style.sheen != null) m = m.background(style.sheen)
    return m
}
