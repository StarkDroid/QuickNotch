package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.starkdroid.quicknotch.datasource.SystemAccent
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
 * diagonal light sheen — which is what reads visually as glass. A literal
 * gaussian blur of the pixels behind a borderless transparent window is not
 * reachable from Compose without a native screen-capture loop (which would
 * also recapture the island itself). [NotchSurface.SYSTEM_ACCENT] is that same
 * glass with the desktop's accent colour blended into the body, rim and a
 * faint sheen glint. The accent is passed in by callers (see [SystemAccent]);
 * the accented style is rebuilt only when that colour changes, so switching the
 * desktop accent recolours the notch in place without a restart.
 */
internal object IslandSurface {

    data class Style(
        val fill: Brush,
        val sheen: Brush?,
        val border: Color,
        val borderWidth: Float
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
            sheen = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.35f to Color.White.copy(alpha = 0.025f),
                0.7f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.05f)
            ),
            border = Color.White.copy(alpha = 0.28f),
            borderWidth = 0.75f
        )
    )

    // The accent style is a function of the OS accent, so it can't live in the
    // static map above; it is rebuilt only when the accent value changes (a
    // rare, user-driven event) and cached per value so steady-state rendering
    // never reallocates brushes between polls.
    @Volatile private var accented: Style? = null
    @Volatile private var accentedFor: Color? = null

    fun styleFor(
        surface: NotchSurface,
        accent: Color? = SystemAccent.current()
    ): Style =
        if (surface == NotchSurface.SYSTEM_ACCENT) {
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
internal fun Modifier.islandSurface(surface: NotchSurface): Modifier {
    val style = IslandSurface.styleFor(surface)
    var m = this.background(style.fill)
    if (style.sheen != null) m = m.background(style.sheen)
    return m
}
