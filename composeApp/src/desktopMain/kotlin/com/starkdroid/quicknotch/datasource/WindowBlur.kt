package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.unit.DpSize
import com.starkdroid.quicknotch.model.NotchStyle
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Color
import java.awt.Rectangle
import java.awt.Window

/**
 * Real backdrop blur behind the notch island, per platform.
 *
 * macOS shows a dedicated helper window (see the
 * `quicknotch_set_content_blur` export in `passthrough_bridge.m`), sized to
 * the island rect while it animates; the intensity slider drives both its
 * compositor blur radius and its effect view's opacity.
 *
 * Windows uses the same shape of solution, and for the same native reason:
 * DWM blur must stay off the overlay's own window: the acrylic accent is
 * composed for the **whole window bounds** and ignores any `SetWindowRgn`
 * clip (verified live — the backdrop painted straight over a region-clipped
 * window, and a later circle-region probe left the frost untouched), so
 * applying it to the overlay would smear a blur slab across the
 * fixed-footprint window's transparent margin around the island. Instead a
 * small helper window — invisible-content, island-sized, parked directly
 * below the overlay — carries classic DWM blur-behind (`DwmEnableBlurBehind
 * Window`), the one DWM blur that honours a region: the island's rounded
 * shape (rounded GDI region rebuilt per move, OS frame rounding explicitly
 * OFF) frosts corner-for-corner with the drawn island while everything
 * outside stays sharp. The overlay draws only a dark scrim whose alpha
 * carries the intensity slider ([IslandSurface] blur style) over the frost
 * for legibility. The helper tracks the animated island through
 * [updateIsland], exactly like the macOS panel. With any other surface (or
 * when the HWND cannot be resolved) the island renders exactly as before.
 */
internal class WindowsBackdropBlur private constructor(
    private val overlayHwnd: WinDef.HWND,
    private val helper: Window,
    private val helperHwnd: WinDef.HWND
) {

    /** Whether the blur is currently requested by the surface choice.
     *  Read by the reconcile thread, written on the EDT. */
    @Volatile
    private var enabled = false

    /** Last accent level that applied successfully; drives re-apply on change. */
    private var appliedLevel = -1

    /** Failure already reported once; further failed applies stay silent. */
    private var reported = false

    /** Last island rect fed in (window-client px), so a re-enable re-places.
     *  Also read by the reconcile thread. */
    @Volatile
    private var lastRect: Rectangle? = null

    /** Corner radius (physical px) and top-rounding of the island shape at the
     *  last feed — the helper's rounded region is rebuilt from these, so the
     *  frost follows the pill's stadium as well as the dock's rounded box. */
    @Volatile
    private var lastRadiusPx: Int = 0

    @Volatile
    private var lastRoundTop: Boolean = true

    /** Whether the helper is currently shown through AWT. Writes go through
     *  [setHelperVisible] only, so a per-frame [placeFor] during the morph
     *  never re-shows an already-visible window (an AWT show re-raises the
     *  native window to the front — above the overlay — until the next
     *  SetWindowPos parks it back, which read exactly as content flicker). */
    @Volatile
    private var helperVisible = true

    /** Last mirrored WS_EX_TOPMOST state, so per-tick places don't re-toggle.
     *  Written from the EDT and the reconcile thread; both write the same
     *  value, so a stale read costs one redundant SetWindowPos at worst. */
    @Volatile
    private var lastTopmost: Boolean? = null

    /** Set by [close]; stops the z-order reconcile loop. */
    @Volatile
    private var closed = false

    init {
        reconcile()
    }

    fun setBlur(enabled: Boolean, intensity: Int) {
        this.enabled = enabled
        val level = intensity.coerceIn(BlurRadiusMin, BlurRadiusMax)
        if (!enabled) {
            appliedLevel = -1
            runCatching {
                enableGlassBlur(helperHwnd, null)
                setHelperVisible(false)
            }
            return
        }
        if (level == appliedLevel && helperVisible) return
        // Any acrylic tint is cleared: the frost below is classic DWM blur,
        // which honours the rounded region — the acrylic backdrop does not.
        runCatching { applyAccent(helperHwnd, false, level) }
        appliedLevel = level
        reported = false
        // Place as far as known geometry allows; a not-yet-measured island
        // just leaves the helper hidden until the first updateIsland feed.
        // Unlike the old acrylic path there is no HRESULT to gate on here:
        // the per-move glass push reports through [reported] instead, and a
        // failed push simply leaves the previous frame's frost up.
        placeFor(lastRect, lastRadiusPx, lastRoundTop)
    }

    /**
     * Tracks the drawn island's rect (window-client physical px — the same
     * feed the click-through region uses) plus the island shape's corner
     * radius, so the helper blur expands, collapses and re-rounds exactly with
     * the notch. Rect changes arrive on every layout pass during the spring,
     * which is precisely when the helper must move.
     */
    fun updateIsland(rect: Rectangle?, radiusPx: Int = 0, roundTop: Boolean = true) {
        val changed = rect != lastRect || radiusPx != lastRadiusPx || roundTop != lastRoundTop
        if (!changed) return
        lastRect = rect?.clone() as Rectangle?
        lastRadiusPx = radiusPx
        lastRoundTop = roundTop
        if (!enabled) return
        placeFor(lastRect, lastRadiusPx, lastRoundTop)
    }

    fun close() {
        closed = true
        runCatching { helper.dispose() }
    }

    /**
     * One AWT visibility transition, at most, per actual state change — never
     * a re-show per animation frame (see [helperVisible]).
     */
    private fun setHelperVisible(visible: Boolean) {
        if (visible == helperVisible) return
        helperVisible = visible
        runCatching { helper.isVisible = visible }
    }

    /**
     * Moves/resizes the helper onto [rect] translated to screen space via the
     * overlay's own native rect (physical px end to end — no AWT unit
     * conversion anywhere), rounds it to the island's corners, keeps it under
     * the overlay in the Z order, and mirrors the overlay's topmost state so
     * "always on top" off keeps the blur band from floating over the overlay.
     * False when the rect is not known yet: the helper then stays hidden until
     * the island is measured.
     *
     * Ordering matters: the AWT show comes first because showing can front
     * the window, and the native park below the overlay always runs after it.
     */
    private fun placeFor(rect: Rectangle?, radiusPx: Int, roundTop: Boolean): Boolean {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            setHelperVisible(false)
            return false
        }
        setHelperVisible(true)
        mirrorTopmost()
        val placed = placeNative(rect, radiusPx, roundTop)
        return placed
    }

    /**
     * Z-order + geometry + frost, natively only (no AWT calls — safe from the
     * reconcile thread). One SetWindowPos does the move: passing the overlay
     * as `hWndInsertAfter` parks the helper *directly below* the overlay, so
     * no separate "raise the overlay" pass exists to be undone later.
     * NOCOPYBITS skips the bit-blit of the old surface on resize — the window
     * is contentless glass, there is nothing worth smearing one frame over.
     *
     * The frost is classic DWM blur-behind, NOT the acrylic accent: only the
     * blur-behind call honours a region, so only it can frost the rounded
     * island without also frosting a sharp rectangle around it. The same
     * region doubles as the window region (click-through outside the island,
     * region-shaped shadow), so one build serves both calls.
     */
    private fun placeNative(rect: Rectangle, radiusPx: Int, roundTop: Boolean): Boolean {
        val origin = WinDef.RECT()
        if (!User32.INSTANCE.GetWindowRect(overlayHwnd, origin)) return false
        val moved = User32.INSTANCE.SetWindowPos(
            helperHwnd,
            overlayHwnd,
            origin.left + rect.x,
            origin.top + rect.y,
            rect.width,
            rect.height,
            SwpNoActivate or SwpNoCopyBits
        )
        // AWT still believes the helper is 1×1, so it never repaints the
        // grown area itself — and the glass key must be black everywhere the
        // region frosts. Fill natively (no AWT: no raise side effects, safe
        // from any thread) right after the move.
        runCatching { paintBlackKey(helperHwnd, rect.width, rect.height) }
        applyGlassRegion(rect.width, rect.height, radiusPx, roundTop)
        return moved
    }

    /**
     * Frosts the helper exactly inside the island's rounded shape
     * (helper-local coords). DWM copies the region at the blur call, while
     * SetWindowRgn takes ownership on success — so the one built region feeds
     * the blur first and the window second, and is only released when the
     * window call fails to take it. Square-top (flush) styles keep square top
     * corners: a plain round-rect would nip them, so the region is a top rect
     * unioned with a rounded bottom band.
     */
    private fun applyGlassRegion(width: Int, height: Int, radiusPx: Int, roundTop: Boolean) {
        val region = buildIslandRegion(width, height, radiusPx, roundTop)
        if (region == null) {
            // Degenerate shape: frost off rather than a sharp full-window
            // slab, and the window region already cleared by the builder.
            runCatching { enableGlassBlur(helperHwnd, null) }
            return
        }
        val glassOk = runCatching { enableGlassBlur(helperHwnd, region) }.getOrElse { false }
        if (!glassOk && !reported) {
            reported = true
            System.err.println("QuickNotch: Windows backdrop blur failed to apply (DWM blur behind)")
        }
        // The window owns the region after a successful call — deleting it
        // would pull the shape out from under the window. A failed call owns
        // nothing, so the handle is released to avoid a GDI leak per frame.
        if (User32.INSTANCE.SetWindowRgn(helperHwnd, region, true) == 0) {
            runCatching { gdi32.DeleteObject(region) }
        }
    }

    /**
     * Builds the island's rounded shape in helper-local coords, or null when
     * GDI fails — the caller then keeps the previous frame's frost instead of
     * pushing a half-built shape.
     */
    private fun buildIslandRegion(
        width: Int,
        height: Int,
        radiusPx: Int,
        roundTop: Boolean
    ): WinDef.HRGN? {
        val gdi = gdi32
        val radius = radiusPx.coerceIn(0, minOf(width, height) / 2)
        if (radius <= 0) {
            User32.INSTANCE.SetWindowRgn(helperHwnd, null, true)
            return null
        }
        return if (roundTop) {
            gdi.CreateRoundRectRgn(0, 0, width, height, radius * 2, radius * 2)
        } else {
            val top = gdi.CreateRectRgn(0, 0, width, (height - radius).coerceAtLeast(0))
            val bottom = gdi.CreateRoundRectRgn(
                0, height - radius * 2, width, height, radius * 2, radius * 2
            )
            val combined = gdi.CreateRectRgn(0, 0, 0, 0)
            gdi.CombineRgn(combined, top, bottom, RgnOr)
            gdi.DeleteObject(top)
            gdi.DeleteObject(bottom)
            combined
        }
    }

    /**
     * Mirrors the overlay's WS_EX_TOPMOST onto the helper — through native
     * calls only. AWT's `isAlwaysOnTop` must never be used here: it books the
     * window in AWT's own always-on-top registry, and AWT re-asserts that
     * bookkeeping on its own schedule (any later show/reshape of an AWT window
     * re-raises every registered one), which put the accent slab back on top
     * of the island content within seconds and covered the whole dock. Native
     * styles are invisible to AWT, so nothing re-raises them; the periodic
     * [reconcile] then only repairs genuine external z-order churn.
     */
    private fun mirrorTopmost() {
        val topmost = User32.INSTANCE
            .GetWindowLong(overlayHwnd, GwlExStyle).toInt() and WsExTopmost != 0
        if (topmost == lastTopmost) return
        val style = User32.INSTANCE.GetWindowLong(helperHwnd, GwlExStyle).toInt()
        User32.INSTANCE.SetWindowLong(
            helperHwnd, GwlExStyle,
            if (topmost) style or WsExTopmost else style and WsExTopmost.inv()
        )
        // Band change only — the geometry pass (placeNative) follows on the
        // EDT path; the reconcile loop re-runs it for the non-EDT caller.
        User32.INSTANCE.SetWindowPos(
            helperHwnd,
            if (topmost) HwndTopmost else HwndNotopmost,
            0, 0, 0, 0,
            SwpNoMove or SwpNoSize or SwpNoActivate
        )
        lastTopmost = topmost
    }

    /**
     * ~700 ms re-assert while enabled, the same cadence the macOS blur panel
     * uses for the identical fight: fullscreen apps, taskbar toggles and other
     * topmost churn can bury the helper (or drop it out of the topmost band)
     * between island updates — which only arrive on layout changes, so a
     * wedged z-order could sit wrong for minutes. Native-only, no AWT.
     */
    private fun reconcile() {
        val thread = Thread({
            while (!closed) {
                runCatching { Thread.sleep(ReconcileIntervalMs) }
                val rect = lastRect
                if (enabled && rect != null) {
                    runCatching { mirrorTopmost(); placeNative(rect, lastRadiusPx, lastRoundTop) }
                }
            }
        }, "quicknotch-blur-z")
        thread.isDaemon = true
        thread.start()
    }

    companion object {
        private const val GwlExStyle = -20
        private const val WsExToolWindow = 0x00000080
        private const val WsExNoActivate = 0x08000000
        private const val WsExTopmost = 0x00000008
        private const val WsExTransparent = 0x00000020
        private const val SwpNoSize = 0x0001
        private const val SwpNoMove = 0x0002
        private const val SwpNoActivate = 0x0010
        private const val SwpNoCopyBits = 0x0100

        /** SetWindowPos `hWndInsertAfter` magic values for the topmost band. */
        private val HwndTopmost = WinDef.HWND(Pointer(-1L))
        private val HwndNotopmost = WinDef.HWND(Pointer(-2L))

        /** The invisible helper window carrying the accent, plus its HWND. */
        private data class HelperWindow(val window: Window, val handle: Long)

        /** Z-order re-assert cadence; matches the macOS blur panel's loop. */
        private const val ReconcileIntervalMs = 700L

        fun install(window: Window?): WindowsBackdropBlur? {
            val handle = if (window != null && HostPlatform.isWindows) {
                resolvePeerLong(windowPeer(window), "getHWnd")
            } else {
                null
            }
            if (handle == null || handle == 0L || !isCompositionEnabled()) {
                // Silent off-Windows (the mac side owns this host); on Windows
                // either cause (unresolvable HWND or DWM composition off)
                // means no blur of any kind, worth one line.
                if (HostPlatform.isWindows) {
                    System.err.println("QuickNotch: Windows backdrop blur off (no HWND or DWM composition)")
                }
                return null
            }
            // The helper is created hidden behind an invisible contentless
            // surface: fully transparent (alpha-0 layered) and accent-free
            // until the Blurred surface turns it on, so it is inert for
            // SOLID/glass users while still proving the capability up front.
            // Topmost is NOT set through AWT here (see [mirrorTopmost]): the
            // first placeFor mirrors the overlay's band natively.
            return createHelperWindow()?.let { helper ->
                try {
                    // WS_EX_TOOLWINDOW keeps the helper out of the taskbar and
                    // Alt+Tab; WS_EX_NOACTIVATE keeps it from stealing focus;
                    // WS_EX_TRANSPARENT makes it mouse-transparent so it never
                    // takes a click even inside the island (the overlay above
                    // owns the whole touch target). It never receives input
                    // anyway — but the style makes that structural.
                    val style = User32.INSTANCE.GetWindowLong(WinDef.HWND(Pointer(helper.handle)), GwlExStyle)
                    User32.INSTANCE.SetWindowLong(
                        WinDef.HWND(Pointer(helper.handle)),
                        GwlExStyle,
                        style or WsExToolWindow or WsExNoActivate or WsExTransparent
                    )
                } catch (_: Throwable) {
                    // Cosmetic only: a taskbar blip must not take the blur down.
                }
                // The OS frame must stay square (DONOTROUND): DWM's own
                // rounding (~8px on every corner) intersects the custom
                // region below, so ROUND would nip the island's 13–18dp
                // corners down to 8px AND round the flush style's square top
                // — the exact "two layers disagree" mismatch. The region
                // alone carries the shape, corner for corner with the island.
                runCatching {
                    dwmapi.DwmSetWindowAttribute(
                        WinDef.HWND(Pointer(helper.handle)),
                        DwmwaWindowCornerPreference,
                        IntByReference(DwmwcpDonotround),
                        Int.SIZE_BYTES
                    )
                }
                WindowsBackdropBlur(
                    WinDef.HWND(Pointer(handle)),
                    helper.window,
                    WinDef.HWND(Pointer(helper.handle))
                )
            } ?: run {
                System.err.println("QuickNotch: Windows backdrop blur off (helper window failed)")
                null
            }
        }

        /**
         * Shows the contentless helper and resolves its HWND. Null (with the
         * half-made window disposed) when either step fails, so the caller has
         * one early-out instead of two.
         *
         * The helper is OPAQUE BLACK, not transparent: classic DWM blur-behind
         * does not combine with layered (`UpdateLayeredWindow`) windows — on
         * an alpha-0 helper the `DwmEnableBlurBehindWindow` call succeeds and
         * blurs nothing. On an opaque window the black is the glass key: DWM
         * frosts it, and the rounded window region clips everything outside
         * the island (shape, shadow and click-through alike).
         */
        private fun createHelperWindow(): HelperWindow? {
            val helper = runCatching {
                Window(null).apply {
                    background = Color(0, 0, 0)
                    setFocusableWindowState(false)
                    setSize(1, 1)
                    isVisible = true
                }
            }.getOrNull() ?: return null
            val handle = resolvePeerLong(windowPeer(helper), "getHWnd")
            return if (handle == null || handle == 0L) {
                runCatching { helper.dispose() }
                null
            } else {
                HelperWindow(helper, handle)
            }
        }
    }
}

/** WINCOMPATTRDATA for `SetWindowCompositionAttribute` (undocumented Win32). */
class WinCompAttrData : Structure() {
    @JvmField var attribute: Int = 0
    @JvmField var data: Pointer? = null
    @JvmField var sizeOfData: Int = 0

    init {
        // Match MSVC packing exactly: JNA's default alignment disagrees with
        // the C struct on the trailing ULONG otherwise.
        setAlignType(ALIGN_MSVC)
    }

    override fun getFieldOrder(): List<String> = listOf("attribute", "data", "sizeOfData")
}

/**
 * ACCENT_POLICY payload: acrylic on, dark tint; all zeros disables.
 *
 * Public, not file-private: JNA derives the struct layout by *reflecting on
 * the fields from `com.sun.jna.Structure` itself*, which throws
 * `IllegalAccessException` on a JVM-non-public class — that failure used to
 * be swallowed whole and read as "DWM composition off?" while the real
 * native call was never even reached.
 */
class AccentPolicy : Structure() {
    @JvmField var accentState: Int = 0
    @JvmField var accentFlags: Int = 0
    @JvmField var gradientColor: Int = 0
    @JvmField var animationId: Int = 0

    init {
        setAlignType(ALIGN_MSVC)
    }

    override fun getFieldOrder(): List<String> =
        listOf("accentState", "accentFlags", "gradientColor", "animationId")
}

private interface User32Ex : Library {
    // Exact Win32 spelling required: JNA maps the method name straight onto
    // the native export, so the Kotlin naming rule is suppressed just here.
    @Suppress("FunctionNaming")
    fun SetWindowCompositionAttribute(hwnd: WinDef.HWND, data: WinCompAttrData): Boolean

    @Suppress("FunctionNaming")
    fun FillRect(hdc: WinDef.HDC, rect: WinDef.RECT, brush: WinDef.HBRUSH): Int
}

private interface GdiBrushEx : Library {
    // Exact Win32 spelling, same rule as above.
    @Suppress("FunctionNaming")
    fun GetStockObject(brush: Int): WinDef.HBRUSH
}

private val gdi32 = GDI32.INSTANCE

private val gdiBrush: GdiBrushEx by lazy {
    Native.load("gdi32", GdiBrushEx::class.java)
}

/** GetStockObject id for the solid black brush (no create/delete needed). */
private const val BlackBrush = 4

/**
 * Fills [hwnd]'s client area with the opaque black glass key. Native only:
 * AWT's peer still records the 1×1 creation size, so an AWT repaint would
 * paint one pixel and leave the rest to chance.
 */
private fun paintBlackKey(hwnd: WinDef.HWND, width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    val hdc = User32.INSTANCE.GetDC(hwnd) ?: return
    try {
        val brush = gdiBrush.GetStockObject(BlackBrush)
        val rect = WinDef.RECT()
        rect.left = 0
        rect.top = 0
        rect.right = width
        rect.bottom = height
        rect.write()
        accentUser32.FillRect(hdc, rect, brush)
    } finally {
        User32.INSTANCE.ReleaseDC(hwnd, hdc)
    }
}

private interface DwmapiEx : Library {
    // Exact Win32 spelling, same rule as above.
    @Suppress("FunctionNaming")
    fun DwmSetWindowAttribute(
        hwnd: WinDef.HWND,
        dwAttribute: Int,
        pvAttribute: IntByReference,
        cbAttribute: Int
    ): Int

    @Suppress("FunctionNaming")
    fun DwmEnableBlurBehindWindow(hwnd: WinDef.HWND, blurBehind: DwmBlurBehind): Int

    @Suppress("FunctionNaming")
    fun DwmIsCompositionEnabled(enabled: IntByReference): Int
}

private val dwmapi: DwmapiEx by lazy {
    Native.load("dwmapi", DwmapiEx::class.java)
}

/** CombineRgn mode: union of both sources. */
private const val RgnOr = 2

/** DWMWA_WINDOW_CORNER_PREFERENCE values: the OS frame must NOT round. */
private const val DwmwaWindowCornerPreference = 33
private const val DwmwcpDonotround = 1

/** DWM_BLURBEHIND dwFlags: enable + honour the blur region. */
private const val DwmBbEnable = 0x00000001
private const val DwmBbBlurRegion = 0x00000002

/**
 * DWM_BLURBEHIND payload for `DwmEnableBlurBehindWindow` — classic blur
 * behind a region, the one DWM blur that honours a shape.
 *
 * Public, same JNA rule as the structs above: the layout is derived by
 * reflecting on the fields from `com.sun.jna.Structure` itself.
 */
class DwmBlurBehind : Structure() {
    @JvmField var dwFlags: Int = 0
    @JvmField var fEnable: Int = 0
    @JvmField var hRgnBlur: WinDef.HRGN? = null
    @JvmField var fTransitionOnMaximized: Int = 0

    init {
        setAlignType(ALIGN_MSVC)
    }

    override fun getFieldOrder(): List<String> =
        listOf("dwFlags", "fEnable", "hRgnBlur", "fTransitionOnMaximized")
}

/**
 * Pushes classic blur-behind onto [hwnd]: enabled with [region], or fully off
 * with null. DWM copies the region at the call, so the caller keeps
 * ownership (unlike SetWindowRgn, which takes it). True on S_OK.
 */
private fun enableGlassBlur(hwnd: WinDef.HWND, region: WinDef.HRGN?): Boolean {
    val behind = DwmBlurBehind()
    behind.dwFlags = DwmBbEnable or DwmBbBlurRegion
    behind.fEnable = if (region != null) 1 else 0
    behind.hRgnBlur = region
    behind.fTransitionOnMaximized = 0
    behind.write()
    return dwmapi.DwmEnableBlurBehindWindow(hwnd, behind) == 0
}

/** False when DWM composition is off — no blur of any kind can apply. */
private fun isCompositionEnabled(): Boolean {
    val enabled = IntByReference()
    return runCatching { dwmapi.DwmIsCompositionEnabled(enabled) == 0 && enabled.value != 0 }
        .getOrDefault(false)
}

private val accentUser32: User32Ex by lazy {
    Native.load("user32", User32Ex::class.java)
}

private const val WcaAccentPolicy = 19
private const val AccentEnableAcrylicBlurBehind = 4

/** Flips the DWM accent policy on [hwnd]; all-zero policy disables blur. */
private fun applyAccent(hwnd: WinDef.HWND, enabled: Boolean, intensity: Int): Boolean {
    val policy = AccentPolicy()
    if (enabled) {
        policy.accentState = AccentEnableAcrylicBlurBehind
        policy.accentFlags = 0
        // DWM has no blur-radius knob, so the intensity slider drives what it
        // does expose: the acrylic tint alpha, from near-clear glass (~8%) to
        // deep frost (~70%). This is the OS acrylic either way — the slider
        // never swaps in a painted imitation, it only tints the real thing.
        policy.gradientColor = (0x14 + intensity * 4).coerceIn(0x14, 0xE6) shl 24
        policy.animationId = 0
    }
    policy.write()
    val data = WinCompAttrData()
    data.attribute = WcaAccentPolicy
    data.data = policy.pointer
    data.sizeOfData = policy.size()
    data.write()
    return accentUser32.SetWindowCompositionAttribute(hwnd, data)
}

/** Island size, corner mask and intensity for the helper window. */
internal data class BlurFrame(
    val w: Double,
    val h: Double,
    val cornerRadius: Double,
    val roundTop: Boolean,
    val intensity: Double
)

/**
 * macOS side of the backdrop blur: resolves the NSWindow once, then drives
 * the helper blur window (created, island-sized, torn down inside the
 * bridge). The frosted shape tracks the animated island, so it expands and
 * collapses exactly with the notch.
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
