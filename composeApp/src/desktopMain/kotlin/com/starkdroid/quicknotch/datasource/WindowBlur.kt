package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.unit.DpSize
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * Real backdrop blur behind the notch island, per platform.
 *
 * macOS parks an NSVisualEffectView under the Skiko canvas (see the
 * `quicknotch_set_content_blur` export in `passthrough_bridge.m`), sized to
 * the island rect on every size frame because the island animates. Windows
 * flips the window's DWM accent policy to acrylic once per toggle — no rect
 * is needed there because the existing `SetWindowRgn` clip already follows
 * the island. Either way the Compose side draws only the dark scrim
 * ([IslandSurface] blur style) for legibility; with the toggle off (or when
 * any handle/bridge is missing) the island renders exactly as before.
 *
 * Driven from the overlay, which owns the animated island size: one
 * `LaunchedEffect` on the toggle + size + expand state covers both platforms.
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
            // 60% black tint (0xAABBGGRR): frosted, not milky.
            policy.gradientColor = 0x99000000.toInt()
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

/** Island rect for the macOS effect view, AppKit points (bottom-left origin). */
internal data class BlurFrame(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val radius: Double
)

/**
 * macOS side of the backdrop blur: resolves the NSWindow once, then positions
 * the effect view on every apply. The island hugs the window's top edge, so
 * the AppKit rect (bottom-left origin) is the island size centred horizontally
 * at `windowHeight - islandHeight`. Corner radius mirrors the pill shape
 * (expanded/collapsed capsule values); the drawn scrim covers any mismatch on
 * square-cornered styles.
 */
internal class MacContentBlur(window: Window?) {

    private val ptr: Long =
        if (HostPlatform.isMac && window != null) resolveMacWindowPtr(window) ?: 0L else 0L

    fun apply(enabled: Boolean, island: DpSize, windowSize: DpSize, expanded: Boolean) {
        if (!HostPlatform.isMac || ptr == 0L) return
        if (!enabled) {
            MacAppKit.setContentBlur(ptr, false, BlurFrame(0.0, 0.0, 0.0, 0.0, 0.0))
            return
        }
        // The island hugs the window's top edge; AppKit's origin is
        // bottom-left, so the rect sits at windowHeight - islandHeight.
        MacAppKit.setContentBlur(
            ptr,
            true,
            BlurFrame(
                x = (windowSize.width.value - island.width.value) / 2.0,
                y = (windowSize.height.value - island.height.value).toDouble(),
                w = island.width.value.toDouble(),
                h = island.height.value.toDouble(),
                radius = if (expanded) 18.0 else 14.0
            )
        )
    }
}
