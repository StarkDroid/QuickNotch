package com.starkdroid.quicknotch.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.util.Random

object NoiseGenerator {
    private var cachedNoiseBitmap: ImageBitmap? = null
    private var cachedBrush: ShaderBrush? = null

    fun getNoiseBrush(): ShaderBrush {
        cachedBrush?.let { return it }

        val size = 128
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val rand = Random(1337L)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val brightness = 140 + rand.nextInt(115)
                val alpha = 6 + rand.nextInt(10)
                val argb = (alpha shl 24) or (brightness shl 16) or (brightness shl 8) or brightness
                img.setRGB(x, y, argb)
            }
        }

        val bitmap = img.toComposeImageBitmap()
        cachedNoiseBitmap = bitmap
        val brush = ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
        cachedBrush = brush
        return brush
    }
}
