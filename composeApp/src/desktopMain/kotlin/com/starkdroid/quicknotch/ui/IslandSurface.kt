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
 * The body material for the notch island. All entries resolve to brushes built
 * once and cached, so switching never reallocates and steady-state rendering
 * does no shader work beyond one or two flat paints — the island is repainted
 * by the software renderer every animation frame, so nothing here may depend
 * on size, time or per-pixel texture.
 *
 * [NotchSurface.TRANSLUCENT] and [NotchSurface.SYSTEM_ACCENT] are deliberately
 * texture-free: a mottled or grainy overlay reads as paper, not glass. They
 * instead compose two perfectly smooth gradients — a translucent tinted body
 * that lets the real desktop bleed through the transparent window, plus a soft
 * diagonal light sheen — which is what reads visually as glass. [NotchSurface.SYSTEM_ACCENT] is that same
 * glass with the desktop's accent colour blended into the body, rim and a
 * faint sheen glint. The accent is passed in by callers (see [SystemAccent]);
 * the accented style is rebuilt only when that colour changes, so switching the
 * desktop accent recolours the notch in place without a restart.
 *
 * A literal backdrop blur comes from the OS window, not from Compose: with
 * the Blurred surface active, the native bridge blurs the desktop behind the
 * island and the island itself draws only the dark scrim below for legibility.
 * Where the bridge is missing, Blurred falls back to the painted glass.
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
     * Light dark scrim drawn over the natively blurred backdrop when the
     * Blurred surface is active and the native bridge answered: the OS
     * supplies the frosted texture, this supplies the contrast. Classic DWM
     * blur (and the macOS panel) blur without darkening, so the scrim carries
     * the whole intensity ramp: near-clear glass at 0, deep frost at the max.
     */
    private fun blurScrimFor(level: Int): Style {
        val clamped = level.coerceIn(BlurRadiusMin, BlurRadiusMax)
        val alpha = (ScrimBaseAlpha + clamped * ScrimAlphaPerStep).coerceIn(0f, 0.6f)
        return Style(
            fill = SolidColor(Color.Black.copy(alpha = alpha)),
            sheen = glassSheen,
            // No rim in blurred mode: the frost's own edge already delimits the
            // island against the desktop, and a white hairline drawn over it
            // reads as a bright border around the blur.
            border = Color.Transparent,
            borderWidth = 0f
        )
    }

    // Scrim ramp for the blurred surface: the native frost blurs without
    // darkening, so the scrim alpha carries the intensity slider end to end
    // (near-clear glass at 0, deep frost at the max). Kept deliberately
    // lighter than the old acrylic-era total: the untinted frost already
    // reads dark over most desktops, and the slider must bottom out at
    // almost-clear glass.
    private const val ScrimBaseAlpha = 0.06f
    private const val ScrimAlphaPerStep = 0.008f

    // The accent style is a function of the OS accent, so it can't live in the
    // static map above; it is rebuilt only when the accent value changes (a
    // rare, user-driven event) and cached per value so steady-state rendering
    // never reallocates brushes between polls.
    @Volatile private var accented: Style? = null
    @Volatile private var accentedFor: Color? = null

    fun styleFor(
        surface: NotchSurface,
        accent: Color?,
        blurred: Boolean = false,
        blurLevel: Int = BlurRadiusMax
    ): Style =
        if (blurred) {
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
    blurLevel: Int = BlurRadiusMax
): Modifier {
    val style = IslandSurface.styleFor(surface, accent, blurred, blurLevel)
    var m = this.background(style.fill)
    if (style.sheen != null) m = m.background(style.sheen)
    return m
}
