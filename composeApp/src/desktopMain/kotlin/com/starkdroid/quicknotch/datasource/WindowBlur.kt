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
 * macOS shows a dedicated helper window holding one effect view (see the
 * `quicknotch_set_content_blur` export in `passthrough_bridge.m`), sized to
 * the island rect while it animates. Windows flips the window's DWM accent
 * policy to acrylic once per surface change — no rect is needed there because
 * the existing `SetWindowRgn` clip already follows the island. Compose draws
 * a translucent dark scrim ([IslandSurface] blur style) over the effect for
 * legibility; with any other surface (or when any handle/bridge is missing)
 * the island renders exactly as before.
 */
internal class WindowsBackdropBlur private constructor(private val hwnd: WinDef.HWND) {

    /** Last applied state, so per-frame overlay ticks don't re-apply. */
    private var applied = false
    private var reported = false

    fun setBlur(enabled: Boolean, intensity: Int) {
        val level = intensity.coerceIn(BlurRadiusMin, BlurRadiusMax)
        if (enabled == applied && level == appliedLevel) return
        val ok = runCatching { applyAccent(enabled, level) }.getOrElse { false }
        if (ok) {
            applied = enabled
            appliedLevel = level
        } else if (!reported) {
            reported = true
            System.err.println("QuickNotch: Windows backdrop blur failed to apply (DWM composition off?)")
        }
    }

    private var appliedLevel = BlurRadiusMin

    private fun applyAccent(enabled: Boolean, intensity: Int): Boolean {
        val policy = AccentPolicy()
        if (enabled) {
            policy.accentState = ACCENT_ENABLE_ACRYLICBLURBEHIND
            policy.accentFlags = 0
            // DWM has no radius knob, so intensity scales the tint instead:
            // 0xAABBGGRR with alpha from ~20% (clear) to ~50% (frosted).
            // Unverified on Windows hardware — see the mac side for the
            // behaviour contract.
            policy.gradientColor = (0x33 + intensity * 2).coerceIn(0x33, 0xCC) shl 24
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

    fun apply(enabled: Boolean, island: DpSize, intensity: Int) {
        if (!HostPlatform.isMac || ptr == 0L) return
        if (!enabled) {
            MacAppKit.setContentBlur(ptr, false, 0.0, 0.0, 0L)
            return
        }
        MacAppKit.setContentBlur(
            ptr,
            true,
            island.width.value.toDouble(),
            island.height.value.toDouble(),
            intensity.coerceIn(BlurRadiusMin, BlurRadiusMax).toLong()
        )
    }
}

/** Blur intensity slider bounds, shared by the settings tab and both bridges. */
internal const val BlurRadiusMin = 0
internal const val BlurRadiusMax = 40
