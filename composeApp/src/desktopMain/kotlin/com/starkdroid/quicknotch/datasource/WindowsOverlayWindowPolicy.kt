package com.starkdroid.quicknotch.datasource

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Re-asserts `HWND_TOPMOST` on a reconcile tick: AWT sets `WS_EX_TOPMOST` once and a fullscreen
 * game re-raises into that band, leaving the notch underneath. Exclusive fullscreen cannot be overlaid.
 */
internal class WindowsOverlayWindowPolicy private constructor(
    private val window: Window
) : AutoCloseable {

    /** AWT peer HWND, re-resolved if the peer is ever recreated. */
    @Volatile
    private var handle: WinDef.HWND? = resolveHandle()

    @Volatile
    private var wantedAlwaysOnTop = false

    /** Whether this policy (not AWT) put the window into the topmost band. */
    @Volatile
    private var forcedTopmost = false

    @Volatile
    private var gaming = false

    @Volatile
    private var failureLogged = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        if (handle == null) {
            // Peer is usually present by now; if not, currentHandle() retries on every tick.
            System.err.println("QuickNotch: Windows overlay z-order keeper waiting for HWND")
        }
        scope.launch {
            while (isActive) {
                try {
                    apply()
                } catch (_: Throwable) {
                    // Fail soft: one bad tick must never take the overlay down.
                }
                delay(if (gaming) GamingTickMs else IdleTickMs)
            }
        }
    }

    /** Re-targets the policy; [gaming] only changes how often the band is re-asserted. */
    fun update(alwaysOnTop: Boolean, gaming: Boolean) {
        wantedAlwaysOnTop = alwaysOnTop
        this.gaming = gaming
        scope.launch { runCatching { apply() } }
    }

    private fun apply() {
        val wanted = wantedAlwaysOnTop
        // Only undo our own raise: re-asserting NOTOPMOST every tick would keep shoving us below other windows.
        if (!wanted && !forcedTopmost) return
        val hwnd = currentHandle() ?: return
        val flags = WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOACTIVATE or
            WinUser.SWP_NOOWNERZORDER or WinUser.SWP_ASYNCWINDOWPOS
        val insertAfter = if (wanted) HWND_TOPMOST else HWND_NOTOPMOST
        val ok = User32.INSTANCE.SetWindowPos(hwnd, insertAfter, 0, 0, 0, 0, flags)
        if (ok) {
            forcedTopmost = wanted
            failureLogged = false
        } else if (!failureLogged) {
            failureLogged = true
            AppLog.line("Windows overlay z-order: SetWindowPos failed (${Native.getLastError()})")
        }
    }

    private fun currentHandle(): WinDef.HWND? {
        val existing = handle
        if (existing != null && User32.INSTANCE.IsWindow(existing)) return existing
        return resolveHandle().also { handle = it }
    }

    private fun resolveHandle(): WinDef.HWND? =
        resolvePeerLong(windowPeer(window), "getHWnd")
            ?.takeIf { it != 0L }
            ?.let { WinDef.HWND(Pointer(it)) }

    override fun close() {
        scope.cancel()
    }

    companion object {
        /** Idle re-assert: cheap insurance against a window change demoting us. */
        private const val IdleTickMs = 1_500L

        /** While a game owns the screen, recover from a game re-raise fast. */
        private const val GamingTickMs = 250L

        // `Pointer(-1L)`, not `Pointer.createConstant(-1)`: the latter arrives as an invalid handle
        // (SetWindowPos fails 1400), the exact silent no-op this policy exists to prevent.
        private val HWND_TOPMOST = WinDef.HWND(Pointer(-1L))
        private val HWND_NOTOPMOST = WinDef.HWND(Pointer(-2L))

        fun install(window: Window): WindowsOverlayWindowPolicy? {
            if (!HostPlatform.isWindows) return null
            return runCatching { WindowsOverlayWindowPolicy(window) }
                .onFailure {
                    System.err.println("QuickNotch: Windows overlay z-order keeper unavailable: ${it.message}")
                }
                .getOrNull()
        }
    }
}
