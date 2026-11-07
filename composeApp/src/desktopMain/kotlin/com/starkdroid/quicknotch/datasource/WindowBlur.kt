package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import com.starkdroid.quicknotch.model.NotchStyle
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.image.BufferedImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Backdrop blur: a macOS helper panel; on Windows a clamped screen capture,
 * the island hole inpainted, blurred on the CPU. Missing bridge keeps the
 * last frame or painted glass.
 */
internal class WindowsBackdropBlur private constructor(
    private val overlayHwnd: WinDef.HWND,
    /** Physical px to capture beyond the island on every side — at least the
     *  maximum blur sigma, so every pixel under the island samples real
     *  backdrop before the clamped edge treatment can reach it. */
    private val inflatePx: Int,
    /** System scale (dp→px), the same density Compose draws with. */
    private val scale: Float
) {

    /** Whether capture is requested by the surface choice. Written from the
     *  EDT (setBlur), read by the capture thread. */
    @Volatile
    private var enabled = false

    /** Intensity slider value; drives the blur sigma on the capture thread. */
    @Volatile
    private var intensity = BlurRadiusMax

    /** Last island rect fed in (window-client physical px). Written from the
     *  EDT (layout feeds), read by the capture thread. */
    @Volatile
    private var lastRect: Rectangle? = null

    /** Set by [close]; stops the capture loop. */
    @Volatile
    private var closed = false

    /** Latest captured backdrop; the frost leaf redraws on each new frame. */
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    init {
        captureLoop()
    }

    fun setBlur(enabled: Boolean, intensity: Int) {
        this.enabled = enabled
        this.intensity = intensity.coerceIn(BlurRadiusMin, BlurRadiusMax)
        if (!enabled) _frame.value = null
    }

    /**
     * Tracks the island rect in window-client pixels so the capture follows
     * every layout pass.
     */
    fun updateIsland(rect: Rectangle?) {
        lastRect = rect?.clone() as Rectangle?
    }

    fun close() {
        closed = true
    }

    /**
     * ~30 fps while enabled, idle otherwise; the daemon publishes one
     * immutable ImageBitmap.
     */
    private fun captureLoop() {
        val thread = Thread({
            while (!closed) {
                val rect = lastRect.drawableOrNull()
                if (!enabled || rect == null) {
                    runCatching { Thread.sleep(IdlePollMs) }
                    continue
                }
                captureAndPublish(rect)
                runCatching { Thread.sleep(CaptureFrameMs) }
            }
        }, "quicknotch-blur-capture")
        thread.isDaemon = true
        thread.start()
    }

    /** Capture → inpaint self-drawn hole → Gaussian → crop to the island. */
    private fun captureAndPublish(rect: Rectangle) {
        val origin = WinDef.RECT()
        val located = User32.INSTANCE.GetWindowRect(overlayHwnd, origin)
        // Physical px end to end: the compose rect, the window origin
        // (per-monitor-aware process) and the screen DC all speak the same
        // units.
        val islandX = origin.left + rect.x
        val islandY = origin.top + rect.y
        val wantX = islandX - inflatePx
        val wantY = islandY - inflatePx
        val wantW = rect.width + inflatePx * 2
        val wantH = rect.height + inflatePx * 2
        // Clamp into the virtual screen: the island is flush at the top, so
        // the wanted rect always pokes above y=0 and an unclamped BitBlt
        // would fail every frame.
        val vx = User32.INSTANCE.GetSystemMetrics(SmXVirtualScreen)
        val vy = User32.INSTANCE.GetSystemMetrics(SmYVirtualScreen)
        val vw = User32.INSTANCE.GetSystemMetrics(SmCxVirtualScreen)
        val vh = User32.INSTANCE.GetSystemMetrics(SmCyVirtualScreen)
        val capX = if (vw > 0) wantX.coerceIn(vx, vx + vw - 1) else wantX
        val capY = if (vh > 0) wantY.coerceIn(vy, vy + vh - 1) else wantY
        val capW = wantW.coerceAtMost(if (vw > 0) vx + vw - capX else wantW)
        val capH = wantH.coerceAtMost(if (vh > 0) vy + vh - capY else wantH)
        val captured = (
            if (located && capW > 0 && capH > 0) {
                captureScreen(capX, capY, capW, capH)
            } else {
                null
            }
            ) ?: return
        val cap = Rectangle(0, 0, capW, capH)
        // Everything this window paints — scrim, content, the outline ring
        // straddling the edge — lands in the capture and must never reach the
        // backdrop (it would ghost the clock into the blur). The hole is the
        // island rect plus the ring's reach, in capture-local px.
        val pad = (FrostHolePadDp * scale).roundToInt()
        inpaintHole(
            captured,
            cap,
            Rectangle(islandX - capX - pad, islandY - capY - pad, rect.width + pad * 2, rect.height + pad * 2)
        )
        val sigmaPx = (FrostRadiusBaseDp + intensity * FrostRadiusPerStepDp) * scale
        blurArgb(captured, cap, sigmaPx)
        // The crop makes the published bitmap exactly island-sized — the
        // frost leaf blits it 1:1 under the scrim.
        _frame.value = cropBuffer(
            pixels = captured,
            cap = cap,
            crop = Rectangle(islandX - capX, islandY - capY, rect.width, rect.height)
        ).toComposeImageBitmap()
    }

    /**
     * BitBlt to top-down 32bpp ARGB. A native failure returns null and the
     * previous frame stays.
     */
    private fun captureScreen(x: Int, y: Int, width: Int, height: Int): IntArray? =
        User32.INSTANCE.GetDC(null)?.let { screenDc ->
            runCatching {
                val memDc = gdi32.CreateCompatibleDC(screenDc)
                val bitmap = gdi32.CreateCompatibleBitmap(screenDc, width, height)
                try {
                    val previous = gdi32.SelectObject(memDc, bitmap)
                    gdi32.BitBlt(
                        memDc, 0, 0, width, height,
                        screenDc, x, y,
                        Srccopy or Captureblt
                    )
                    // GetDIBits refuses a bitmap selected into a DC — restore
                    // first.
                    gdi32.SelectObject(memDc, previous)
                    val info = WinGDI.BITMAPINFO().apply {
                        bmiHeader.biSize = 40
                        bmiHeader.biWidth = width
                        // Negative height = top-down rows, no mirroring needed.
                        bmiHeader.biHeight = -height
                        bmiHeader.biPlanes = 1
                        bmiHeader.biBitCount = 32
                        bmiHeader.biCompression = 0
                    }
                    val pixels = Memory(width.toLong() * height * 4)
                    val rows = gdi32.GetDIBits(screenDc, bitmap, 0, height, pixels, info, DibRgbColors)
                    if (rows == 0) {
                        null
                    } else {
                        pixels.getIntArray(0, width * height).also { argb ->
                            // 32bpp BI_RGB leaves the alpha byte undefined —
                            // force opaque or the frost would draw
                            // transparent.
                            for (i in argb.indices) argb[i] = argb[i] or OpaqueAlpha
                        }
                    }
                } finally {
                    gdi32.DeleteObject(bitmap)
                    gdi32.DeleteDC(memDc)
                    User32.INSTANCE.ReleaseDC(null, screenDc)
                }
            }.getOrNull()
        }

    /**
     * Replaces the self-drawn hole with the average of a row lerp and a
     * column blend; under the Gaussian the seam disappears.
     */
    private fun inpaintHole(pixels: IntArray, cap: Rectangle, hole: Rectangle) {
        val x0 = hole.x.coerceIn(0, cap.width)
        val x1 = (hole.x + hole.width).coerceIn(0, cap.width)
        val y0 = hole.y.coerceIn(0, cap.height)
        val y1 = (hole.y + hole.height).coerceIn(0, cap.height)
        if (x1 - x0 < 2 || y1 <= y0) return
        val bounds = Rectangle(x0, y0, x1 - x0, y1 - y0)
        lerpRows(pixels, cap, bounds)
        blendColumns(pixels, cap, bounds)
    }

    /**
     * Three separable box passes with clamp-to-edge sampling; the capture is
     * inflated past the island.
     */
    private fun blurArgb(pixels: IntArray, cap: Rectangle, sigmaPx: Float) {
        if (sigmaPx < 0.5f || cap.width < 3 || cap.height < 3) return
        val scratch = IntArray(maxOf(cap.width, cap.height))
        for (box in gaussianBoxes(sigmaPx)) {
            val r = (box - 1) / 2
            if (r <= 0) continue
            boxBlurH(pixels, scratch, cap, r)
            boxBlurV(pixels, scratch, cap, r)
        }
    }

    /** Horizontal box pass; reads a row copy so the in-place slide never sees
     *  its own writes. */
    private fun boxBlurH(pixels: IntArray, scratch: IntArray, cap: Rectangle, r: Int) {
        val win = r * 2 + 1
        for (y in 0 until cap.height) {
            val row = y * cap.width
            System.arraycopy(pixels, row, scratch, 0, cap.width)
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val v = scratch[k.coerceIn(0, cap.width - 1)]
                sr += v and 0xFF
                sg += (v shr 8) and 0xFF
                sb += (v shr 16) and 0xFF
            }
            for (x in 0 until cap.width) {
                pixels[row + x] = OpaqueAlpha or
                    ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val out = scratch[(x - r).coerceIn(0, cap.width - 1)]
                val inn = scratch[(x + r + 1).coerceIn(0, cap.width - 1)]
                sr += (inn and 0xFF) - (out and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            }
        }
    }

    /** Vertical box pass; same contract as [boxBlurH], columns via stride. */
    private fun boxBlurV(pixels: IntArray, scratch: IntArray, cap: Rectangle, r: Int) {
        val win = r * 2 + 1
        for (x in 0 until cap.width) {
            for (y in 0 until cap.height) scratch[y] = pixels[y * cap.width + x]
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val v = scratch[k.coerceIn(0, cap.height - 1)]
                sr += v and 0xFF
                sg += (v shr 8) and 0xFF
                sb += (v shr 16) and 0xFF
            }
            for (y in 0 until cap.height) {
                pixels[y * cap.width + x] = OpaqueAlpha or
                    ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val out = scratch[(y - r).coerceIn(0, cap.height - 1)]
                val inn = scratch[(y + r + 1).coerceIn(0, cap.height - 1)]
                sr += (inn and 0xFF) - (out and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            }
        }
    }

    companion object {
        /** BitBlt rop: plain copy + include layered windows below us. */
        private const val Srccopy = 0x00CC0020
        private const val Captureblt = 0x40000000
        private const val DibRgbColors = 0

        /** Virtual-screen metrics: the notch may straddle a monitor edge and
         *  always pokes above y=0, so capture rects are clamped into it. */
        private const val SmXVirtualScreen = 76
        private const val SmYVirtualScreen = 77
        private const val SmCxVirtualScreen = 78
        private const val SmCyVirtualScreen = 79

        /** Frame cadence while the notch is visible (matches the pill's
         *  animation budget); idle poll while disabled or unmeasured. */
        private const val CaptureFrameMs = 33L
        private const val IdlePollMs = 120L

        fun install(window: Window?): WindowsBackdropBlur? {
            val handle = if (window != null && HostPlatform.isWindows) {
                resolvePeerLong(windowPeer(window), "getHWnd")
            } else {
                null
            }
            if (handle == null || handle == 0L) {
                if (HostPlatform.isWindows) {
                    System.err.println("QuickNotch: Windows backdrop blur off (no HWND)")
                }
                return null
            }
            // Dp→px at the system scale, the same density Compose draws with.
            val scale = runCatching {
                Toolkit.getDefaultToolkit().screenResolution / 96f
            }.getOrDefault(1f).coerceAtLeast(1f)
            return WindowsBackdropBlur(
                overlayHwnd = WinDef.HWND(Pointer(handle)),
                inflatePx = (FrostInflateDp * scale).roundToInt().coerceAtLeast(1),
                scale = scale
            )
        }
    }
}

private val gdi32 = GDI32.INSTANCE

/** Force-opaque mask for the undefined alpha byte of a 32bpp BI_RGB
 *  scanline (int ARGB layout). */
private const val OpaqueAlpha = 0xFF000000.toInt()

/** The island feed, or null while it is unmeasured / degenerate — the capture
 *  thread idles on null instead of blitting an empty strip every frame. */
private fun Rectangle?.drawableOrNull(): Rectangle? = this?.takeIf { it.width > 0 && it.height > 0 }

/**
 * Box widths whose three-pass cascade approximates a Gaussian of [sigma].
 * Standard mapping (three boxes ≈ Gaussian); clamped so a hairline sigma
 * still yields valid, non-negative radii.
 */
private fun gaussianBoxes(sigma: Float): IntArray {
    val n = 3
    val ideal = sqrt(12f * sigma * sigma / n + 1f)
    var lo = floor(ideal).toInt().coerceAtLeast(1)
    if (lo % 2 == 0) lo -= 1
    val hi = lo + 2
    val mIdeal = (12f * sigma * sigma - n * lo * lo - 4f * n * lo - 3f * n) / (-4f * lo - 4)
    val m = round(mIdeal).toInt()
    return IntArray(n) { if (it < m) lo else hi }
}

/** Horizontal half of the hole reconstruction: each row becomes a linear
 *  blend between the real columns flanking the hole, keeping its own
 *  top-to-bottom neighbours. [bounds] is the clamped hole, capture-local. */
private fun lerpRows(pixels: IntArray, cap: Rectangle, bounds: Rectangle) {
    val x0 = bounds.x
    val x1 = bounds.x + bounds.width
    val leftX = (x0 - 1).coerceAtLeast(0)
    val rightX = x1.coerceAtMost(cap.width - 1)
    val span = (rightX - leftX).coerceAtLeast(1)
    for (y in bounds.y until bounds.y + bounds.height) {
        val row = y * cap.width
        val left = pixels[row + leftX]
        val right = pixels[row + rightX]
        val dR = ((right and 0xFF) - (left and 0xFF))
        val dG = (((right shr 8) and 0xFF) - ((left shr 8) and 0xFF))
        val dB = (((right shr 16) and 0xFF) - ((left shr 16) and 0xFF))
        for (x in x0 until x1) {
            val t = (x - leftX).toFloat() / span
            val r = ((left and 0xFF) + dR * t).roundToInt()
            val g = (((left shr 8) and 0xFF) + dG * t).roundToInt()
            val b = (((left shr 16) and 0xFF) + dB * t).roundToInt()
            pixels[row + x] = OpaqueAlpha or (b shl 16) or (g shl 8) or r
        }
    }
}

/**
 * Blends each column between the real rows above and below, then averages
 * with lerpRows.
 */
private fun blendColumns(pixels: IntArray, cap: Rectangle, bounds: Rectangle) {
    val x0 = bounds.x
    val x1 = bounds.x + bounds.width
    val y0 = bounds.y
    val y1 = bounds.y + bounds.height
    // The screen edge may take the row above the hole; below is always real
    // (the capture extends past the island on every side that exists).
    val topY = (y0 - 1).coerceAtLeast(0)
    val bottomY = y1.coerceAtMost(cap.height - 1)
    val spanY = (bottomY - topY).coerceAtLeast(1)
    for (x in x0 until x1) {
        val top = pixels[topY * cap.width + x]
        val bottom = pixels[bottomY * cap.width + x]
        for (y in y0 until y1) {
            val vy = ((y - topY).toFloat() / spanY).coerceIn(0f, 1f)
            val blended = averageChannels(top, bottom, vy)
            val i = y * cap.width + x
            val lerped = pixels[i]
            val r = ((lerped and 0xFF) + (blended and 0xFF)) / 2
            val g = (((lerped shr 8) and 0xFF) + ((blended shr 8) and 0xFF)) / 2
            val b = (((lerped shr 16) and 0xFF) + ((blended shr 16) and 0xFF)) / 2
            pixels[i] = OpaqueAlpha or (b shl 16) or (g shl 8) or r
        }
    }
}

/** One pixel: the per-channel linear blend of [top] toward [bottom] at
 *  [vy] (0 = top row, 1 = bottom row). */
private fun averageChannels(top: Int, bottom: Int, vy: Float): Int {
    val r = ((top and 0xFF) + (((bottom and 0xFF) - (top and 0xFF)) * vy)).roundToInt()
    val g = (((top shr 8) and 0xFF) + (((bottom shr 8) and 0xFF) - ((top shr 8) and 0xFF)) * vy).roundToInt()
    val b = (((top shr 16) and 0xFF) + (((bottom shr 16) and 0xFF) - ((top shr 16) and 0xFF)) * vy).roundToInt()
    return OpaqueAlpha or (b shl 16) or (g shl 8) or r
}

/** Crops the blurred strip to [crop] (clamped defensively, so a screen-edge
 *  capture can never read out of bounds) and wraps it for Compose. */
private fun cropBuffer(pixels: IntArray, cap: Rectangle, crop: Rectangle): BufferedImage {
    val x = crop.x.coerceIn(0, cap.width)
    val y = crop.y.coerceIn(0, cap.height)
    val w = crop.width.coerceAtMost((cap.width - x).coerceAtLeast(0)).coerceAtLeast(1)
    val h = crop.height.coerceAtMost((cap.height - y).coerceAtLeast(0)).coerceAtLeast(1)
    val out = IntArray(w * h)
    for (row in 0 until h) {
        System.arraycopy(pixels, (y + row) * cap.width + x, out, row * w, w)
    }
    return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, w, h, out, 0, w)
    }
}

/**
 * Gaussian sigma the capture thread blurs with; FrostInflateDp must stay
 * above this maximum.
 */
private const val FrostRadiusBaseDp = 4f
private const val FrostRadiusPerStepDp = 0.5f
private const val FrostMaxRadiusDp = 24f

/** Capture inflation beyond the island: three sigmas plus margin, so the
 *  Gaussian's entire sample window at the island rim is real captured
 *  backdrop rather than the inpainted hole (at the max intensity 3σ reaches
 *  72px — the old 40px band left the rim sampling its own reconstruction). */
private const val FrostInflateDp = FrostMaxRadiusDp * 3f + 8f

/** How far past the island edge the self-drawn hole extends when inpainting
 *  — the progress outline ring straddles that edge (2dp pad + stroke) and
 *  would otherwise ghost into the blur. */
private const val FrostHolePadDp = 5f

/** Island size, corner mask and intensity for the macOS helper window. */
internal data class BlurFrame(
    val w: Double,
    val h: Double,
    val cornerRadius: Double,
    val roundTop: Boolean,
    val intensity: Double
)

/**
 * Drives the bridge's helper blur window, which tracks the animated island.
 */
internal class MacContentBlur(window: Window?) {

    private val ptr: Long =
        if (HostPlatform.isMac && window != null) resolveMacWindowPtr(window) ?: 0L else 0L

    /** True once the bridge answers for this window; gates the blur scrim. */
    val available: Boolean
        get() = HostPlatform.isMac && ptr != 0L && MacAppKit.windowResponds(ptr)

    fun apply(
        enabled: Boolean,
        island: DpSize,
        expanded: Boolean,
        style: NotchStyle,
        intensity: Int
    ) {
        if (!HostPlatform.isMac || ptr == 0L) return
        if (!enabled) {
            MacAppKit.setContentBlur(ptr, false, BlurFrame(0.0, 0.0, 0.0, false, 0.0))
            return
        }
        MacAppKit.setContentBlur(
            ptr,
            true,
            BlurFrame(
                w = island.width.value.toDouble(),
                h = island.height.value.toDouble(),
                cornerRadius = if (expanded) 18.0 else if (style.capsule) 14.0 else 13.0,
                roundTop = style.capsule,
                intensity = intensity.coerceIn(BlurRadiusMin, BlurRadiusMax).toDouble()
            )
        )
    }
}

/** Blur intensity slider bounds, shared by the settings tab and both bridges. */
internal const val BlurRadiusMin = 0
internal const val BlurRadiusMax = 40
