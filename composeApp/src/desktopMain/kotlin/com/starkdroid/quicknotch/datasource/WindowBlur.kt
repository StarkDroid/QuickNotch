package com.starkdroid.quicknotch.datasource

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * Real backdrop blur behind the notch island, per platform.
 *
 * macOS sets a WindowServer blur radius on the whole window (see the
 * `quicknotch_set_window_blur_radius` export in `passthrough_bridge.m`).
 * Windows flips the window's DWM accent policy to acrylic once per toggle —
 * no rect is needed there because the existing `SetWindowRgn` clip already
 * follows the island. Compose draws a translucent dark scrim ([IslandSurface]
 * blur style) over the effect for legibility; with the toggle off (or when
 * any handle/bridge is missing) the island renders exactly as before.
 *
 * Driven from the overlay with one `LaunchedEffect` on the toggle; both
 * platforms apply on toggle only, with no per-frame work.
 */
internal class WindowsBackdropBlur private constructor(private val hwnd: WinDef.HWND) {

    /** Last applied state, so per-frame overlay ticks don't re-apply. */
    private var applied = false
    private var reported = false

    fun setEnabled(enabled: Boolean) {
        if (enabled == applied) return
        val ok = runCatching { applyAccent(enabled) }.getOrElse { false }
        if (ok) {
            applied = enabled
        } else if (!reported) {
            reported = true
            System.err.println("QuickNotch: Windows backdrop blur failed to apply (DWM composition off?)")
        }
    }

    private fun applyAccent(enabled: Boolean): Boolean {
        val policy = AccentPolicy()
        if (enabled) {
            policy.accentState = ACCENT_ENABLE_ACRYLICBLURBEHIND
            policy.accentFlags = 0
            // 40% black tint (0xAABBGGRR) leaves the background visible.
            policy.gradientColor = 0x66000000
            policy.animationId = 0
        }
        policy.write()
        val data = WinCompAttrData()
        data.attribute = WCA_ACCENT_POLICY
        data.data = policy.pointer
        data.sizeOfData = policy.size()
        data.write()
        return user32.SetWindowCompositionAttribute(hwnd, data)
    }

    companion object {
        private const val WCA_ACCENT_POLICY = 19
        private const val ACCENT_ENABLE_ACRYLICBLURBEHIND = 4

        private val user32: User32Ex by lazy {
            Native.load("user32", User32Ex::class.java)
        }

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
            return WindowsBackdropBlur(WinDef.HWND(Pointer(handle)))
        }
    }
}

/** WINCOMPATTRDATA for `SetWindowCompositionAttribute` (undocumented Win32). */
private class WinCompAttrData : Structure() {
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

/** ACCENT_POLICY payload: acrylic on, dark tint; all zeros disables. */
private class AccentPolicy : Structure() {
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

/**
 * macOS side of the backdrop blur: resolves the NSWindow once, then sets the
 * compositor blur radius for the whole window. Window-level (not island
 * rects): the margin around the pill reads as a soft halo rather than a
 * masked capsule, which is the documented trade-off of this API.
 */
internal class MacContentBlur(window: Window?) {

    private val ptr: Long =
        if (HostPlatform.isMac && window != null) resolveMacWindowPtr(window) ?: 0L else 0L

    /** True once the bridge answers for this window; gates the blur scrim. */
    val available: Boolean
        get() = HostPlatform.isMac && ptr != 0L && MacAppKit.windowResponds(ptr)

    fun apply(enabled: Boolean) {
        if (!HostPlatform.isMac || ptr == 0L) return
        MacAppKit.setWindowBlurRadius(ptr, if (enabled) WINDOW_BLUR_RADIUS else 0L)
    }

    companion object {
        /** Moderate frost; lower values stay closer to plain translucency. */
        const val WINDOW_BLUR_RADIUS = 30L
    }
}
