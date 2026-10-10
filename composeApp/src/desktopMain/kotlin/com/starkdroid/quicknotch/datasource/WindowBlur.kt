package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.unit.DpSize
import com.starkdroid.quicknotch.model.NotchStyle
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
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
 * the DWM accent backdrop (`SetWindowCompositionAttribute` acrylic) is
 * composed for the **whole window bounds** and ignores any `SetWindowRgn`
 * clip (verified on Windows 11 24H2 — the backdrop painted straight over a
 * region-clipped window), so applying it to the overlay itself would smear a
 * blur slab across the fixed-footprint window's transparent margin around the
 * island. Instead a small helper window — invisible-content, island-sized,
 * parked directly below the overlay — carries the accent; the overlay draws
 * only its translucent dark scrim ([IslandSurface] blur style) over the
 * effect for legibility. The helper tracks the animated island through
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
                applyAccent(helperHwnd, false, level)
                helper.isVisible = false
            }
            return
        }
        if (level == appliedLevel && helper.isVisible) return
        val ok = runCatching { applyAccent(helperHwnd, true, level) }.getOrElse { false }
        if (ok) {
            appliedLevel = level
            reported = false
            // Place as far as known geometry allows; a not-yet-measured island
            // just leaves the helper hidden until the first updateIsland feed.
            placeFor(lastRect)
        } else if (!reported) {
            reported = true
            System.err.println("QuickNotch: Windows backdrop blur failed to apply (helper window)")
        }
    }

    /**
     * Tracks the drawn island's rect (window-client physical px — the same
     * feed the click-through region uses) so the helper blur expands and
     * collapses exactly with the notch. Rect changes arrive on every layout
     * pass during the spring, which is precisely when the helper must move.
     */
    fun updateIsland(rect: Rectangle?) {
        if (rect == lastRect) return
        lastRect = rect?.clone() as Rectangle?
        if (!enabled) return
        placeFor(lastRect)
    }

    fun close() {
        closed = true
        runCatching { helper.dispose() }
    }

    /**
     * Moves/resizes the helper onto [rect] translated to screen space via the
     * overlay's own native rect (physical px end to end — no AWT unit
     * conversion anywhere), keeps the helper under the overlay in the Z order,
     * and mirrors the overlay's topmost state so "always on top" off keeps the
     * blur band from floating over the overlay. False when the rect is not
     * known yet: the helper then stays hidden until the island is measured.
     */
    private fun placeFor(rect: Rectangle?): Boolean {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            runCatching { helper.isVisible = false }
            return false
        }
        mirrorTopmost()
        val placed = placeNative(rect)
        helper.isVisible = true
        return placed
    }

    /**
     * Z-order + geometry, natively only (no AWT calls — safe from the reconcile
     * thread). One SetWindowPos does it all: passing the overlay as
     * `hWndInsertAfter` parks the helper *directly below* the overlay, so no
     * separate "raise the overlay" pass exists to be undone later.
     */
    private fun placeNative(rect: Rectangle): Boolean {
        val origin = WinDef.RECT()
        if (!User32.INSTANCE.GetWindowRect(overlayHwnd, origin)) return false
        return User32.INSTANCE.SetWindowPos(
            helperHwnd,
            overlayHwnd,
            origin.left + rect.x,
            origin.top + rect.y,
            rect.width,
            rect.height,
            SwpNoActivate
        )
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
                    runCatching { mirrorTopmost(); placeNative(rect) }
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
        private const val SwpNoSize = 0x0001
        private const val SwpNoMove = 0x0002
        private const val SwpNoActivate = 0x0010

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
            if (handle == null || handle == 0L) {
                // Silent off-Windows (the mac side owns this host); a failed
                // HWND resolve on Windows itself is worth one line.
                if (HostPlatform.isWindows) {
                    System.err.println("QuickNotch: Windows backdrop blur off (HWND resolve failed)")
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
                    // Alt+Tab; WS_EX_NOACTIVATE keeps it from stealing focus. It
                    // never receives input anyway — the overlay sits above it and
                    // owns the island as its touch target.
                    val style = User32.INSTANCE.GetWindowLong(WinDef.HWND(Pointer(helper.handle)), GwlExStyle)
                    User32.INSTANCE.SetWindowLong(
                        WinDef.HWND(Pointer(helper.handle)),
                        GwlExStyle,
                        style or WsExToolWindow or WsExNoActivate
                    )
                } catch (_: Throwable) {
                    // Cosmetic only: a taskbar blip must not take the blur down.
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
         */
        private fun createHelperWindow(): HelperWindow? {
            val helper = runCatching {
                Window(null).apply {
                    background = Color(0, 0, 0, 0)
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
        // DWM has no radius knob, so intensity scales the tint instead:
        // 0xAABBGGRR with alpha from ~20% (clear) to ~50% (frosted).
        policy.gradientColor = (0x33 + intensity * 2).coerceIn(0x33, 0xCC) shl 24
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
