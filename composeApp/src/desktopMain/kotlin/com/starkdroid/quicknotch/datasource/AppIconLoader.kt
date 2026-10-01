package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt

object AppIconLoader {
    // Cached bitmaps are capped to this edge length. Both the dock and the
    // settings rows draw far smaller, and on the software renderer a row that
    // samples a full-resolution (up to 1024px) icon pays that resample cost
    // every single frame — the main cause of scroll jank once icons loaded.
    internal const val cachedIconMaxPx = 128

    // Scanning a full installed-apps list fires one load per row at once; a
    // little backpressure keeps dozens of native calls (or sips/quicklook
    // processes) from piling up and timing out.
    private val iconLoadGate = Semaphore(
        permits = minOf(8, maxOf(4, Runtime.getRuntime().availableProcessors() / 2))
    )

    fun getCachedIcon(id: String): ImageBitmap? {
        return if (HostPlatform.isMac) {
            MacAppIconLoader.getCachedIcon(id)
        } else {
            Win32ShellIconLoader.getCachedIcon(id)
        }
    }

    suspend fun loadIcon(app: AppItem): ImageBitmap = iconLoadGate.withPermit {
        if (HostPlatform.isMac) {
            MacAppIconLoader.loadIcon(app)
        } else {
            Win32ShellIconLoader.loadIcon(app)
        }
    }

    fun generateMonogramIcon(title: String, size: Int = 64): ImageBitmap {
        val letter = title.trim().take(1).uppercase().ifEmpty { "A" }
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = img.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val hash = title.hashCode()
            val colors = listOf(
                java.awt.Color(99, 102, 241),
                java.awt.Color(56, 189, 248),
                java.awt.Color(168, 85, 247),
                java.awt.Color(236, 72, 153),
                java.awt.Color(34, 197, 94),
                java.awt.Color(245, 158, 11)
            )
            val bg = colors[kotlin.math.abs(hash) % colors.size]
            g2d.color = bg
            g2d.fillRoundRect(2, 2, size - 4, size - 4, 18, 18)
            g2d.color = java.awt.Color(255, 255, 255, 240)
            val fontName = if (HostPlatform.isMac) "SF Pro Display" else "Segoe UI"
            g2d.font = Font(fontName, Font.BOLD, (size * 0.45).toInt())
            val fm = g2d.fontMetrics
            val textWidth = fm.stringWidth(letter)
            val textHeight = fm.ascent
            val x = (size - textWidth) / 2
            val y = ((size - fm.height) / 2) + textHeight
            g2d.drawString(letter, x, y)
        } finally {
            g2d.dispose()
        }
        return img.toComposeImageBitmap()
    }
}

/**
 * Shrinks a freshly decoded icon to at most [AppIconLoader.cachedIconMaxPx]
 * before it is cached and handed to the UI. Halving progressively keeps small
 * sizes sharp (a single jump from 1024px aliases badly).
 */
internal fun ImageBitmap.downscaleForCache(): ImageBitmap {
    if (width <= AppIconLoader.cachedIconMaxPx && height <= AppIconLoader.cachedIconMaxPx) return this

    var awt = toAwtImage()
    while (max(awt.width, awt.height) > AppIconLoader.cachedIconMaxPx * 2) {
        awt = awt.scaleBilinear(awt.width / 2, awt.height / 2)
    }
    val longEdge = max(awt.width, awt.height)
    if (longEdge > AppIconLoader.cachedIconMaxPx) {
        val ratio = AppIconLoader.cachedIconMaxPx.toFloat() / longEdge
        awt = awt.scaleBilinear(
            (awt.width * ratio).roundToInt().coerceAtLeast(1),
            (awt.height * ratio).roundToInt().coerceAtLeast(1)
        )
    }
    return awt.toComposeImageBitmap()
}

private fun BufferedImage.scaleBilinear(w: Int, h: Int): BufferedImage {
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.drawImage(this, 0, 0, w, h, null)
    } finally {
        g.dispose()
    }
    return out
}
