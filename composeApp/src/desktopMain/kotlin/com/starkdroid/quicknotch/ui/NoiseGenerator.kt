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

    // ── Frost ────────────────────────────────────────────────────────────────

    @Volatile
    private var cachedFrostBrush: ShaderBrush? = null

    /**
     * A seamlessly tiling, gaussian-blurred cloud texture used by the [IslandSurface]
     * BLUR material to fake frosted glassmorphism. Built once: a coarse random
     * lattice is bilinearly upsampled (the interpolation is periodic, so the tile
     * repeats without a seam) and then run through two separable box-blur passes
     * — an approximation of gaussian blur that is O(n) per pixel instead of O(n·r).
     * Total cost is a few milliseconds one time; steady-state painting is just
     * the cached [ShaderBrush], identical to [getNoiseBrush].
     */
    fun getFrostBrush(): ShaderBrush {
        cachedFrostBrush?.let { return it }
        synchronized(this) {
            cachedFrostBrush?.let { return it }
            cachedFrostBrush = buildFrostBrush()
            return cachedFrostBrush!!
        }
    }

    private fun buildFrostBrush(): ShaderBrush {
        val lattice = 10
        val size = 160
        val rand = Random(4242L)
        val corners = FloatArray(lattice * lattice) { rand.nextFloat() }
        fun corner(x: Int, y: Int): Float =
            corners[(y % lattice) * lattice + (x % lattice)]

        val base = FloatArray(size * size)
        for (y in 0 until size) {
            val fy = y.toFloat() / size * lattice
            val y0 = fy.toInt(); val ty = fy - y0
            val sy = smooth(ty)
            for (x in 0 until size) {
                val fx = x.toFloat() / size * lattice
                val x0 = fx.toInt(); val tx = fx - x0
                val sx = smooth(tx)
                val top = corner(x0, y0) + (corner(x0 + 1, y0) - corner(x0, y0)) * sx
                val bottom = corner(x0, y0 + 1) + (corner(x0 + 1, y0 + 1) - corner(x0, y0 + 1)) * sx
                base[y * size + x] = top + (bottom - top) * sy
            }
        }

        var values = base
        repeat(2) { values = boxBlurPeriodic(values, size, radius = 6) }

        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (i in values.indices) {
            // Cloud luminance kept low-alpha: the desktop shows through the
            // translucent base fill and the clouds read as depth in the frost.
            val v = values[i].coerceIn(0f, 1f)
            val brightness = (170 + v * 85).toInt()
            val alpha = (5 + v * 24).toInt()
            img.setRGB(i % size, i / size, (alpha shl 24) or (brightness shl 16) or (brightness shl 8) or brightness)
        }
        return ShaderBrush(ImageShader(img.toComposeImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    /** One separable box-blur pass applied horizontally then vertically, with wrap-around edges. */
    private fun boxBlurPeriodic(src: FloatArray, size: Int, radius: Int): FloatArray {
        val horizontal = FloatArray(src.size)
        val window = radius * 2 + 1
        for (y in 0 until size) {
            val row = y * size
            var sum = 0f
            // Seed the sliding window with wrapped indices.
            for (k in -radius..radius) sum += src[row + floorMod(k, size)]
            for (x in 0 until size) {
                horizontal[row + x] = sum / window
                sum += src[row + floorMod(x + radius + 1, size)] - src[row + floorMod(x - radius, size)]
            }
        }
        val vertical = FloatArray(src.size)
        for (x in 0 until size) {
            var sum = 0f
            for (k in -radius..radius) sum += horizontal[floorMod(k, size) * size + x]
            for (y in 0 until size) {
                vertical[y * size + x] = sum / window
                sum += horizontal[floorMod(y + radius + 1, size) * size + x] -
                    horizontal[floorMod(y - radius, size) * size + x]
            }
        }
        return vertical
    }

    private fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b
}
