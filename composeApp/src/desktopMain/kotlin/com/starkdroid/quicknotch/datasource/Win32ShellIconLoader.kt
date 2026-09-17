package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView

object Win32ShellIconLoader {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val fsv: FileSystemView by lazy { FileSystemView.getFileSystemView() }

    fun getCachedIcon(id: String): ImageBitmap? = cache[id]

    suspend fun loadIcon(app: AppItem): ImageBitmap = withContext(Dispatchers.IO) {
        val cached = cache[app.id]
        if (cached != null) return@withContext cached

        val iconBitmap = try {
            val targetPath = app.iconPath ?: app.executablePath
            val file = File(targetPath)
            if (file.exists()) {
                val icon = fsv.getSystemIcon(file)
                iconToImageBitmap(icon, 64)
            } else {
                generateMonogramIcon(app.name)
            }
        } catch (_: Exception) {
            generateMonogramIcon(app.name)
        }

        cache[app.id] = iconBitmap
        iconBitmap
    }
    private fun iconToImageBitmap(icon: Icon?, targetSize: Int = 64): ImageBitmap {
        if (icon == null) return generateMonogramIcon("?")
        val width = icon.iconWidth.coerceAtLeast(1)
        val height = icon.iconHeight.coerceAtLeast(1)
        val bufferedImage = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
        val g2d: Graphics2D = bufferedImage.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val tempImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val tempG2d = tempImage.createGraphics()
            icon.paintIcon(null, tempG2d, 0, 0)
            tempG2d.dispose()
            g2d.drawImage(tempImage, 0, 0, targetSize, targetSize, null)
        } finally {
            g2d.dispose()
        }
        return bufferedImage.toComposeImageBitmap()
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
            g2d.font = java.awt.Font("Segoe UI", java.awt.Font.BOLD, (size * 0.45).toInt())
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
