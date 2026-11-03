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
 * Keeps the Windows overlay in the topmost z-order band for the app's whole
 * life, and much more aggressively while a fullscreen game owns the screen.
 *
 * AWT's `alwaysOnTop` sets `WS_EX_TOPMOST` exactly once, when the property is
 * assigned. That is enough for a normal desktop, but a fullscreen game raises
 * its own window into the same band on every mode switch / foreground grab,
 * and Windows orders topmost windows by recency: the game ends up above the
 * notch until some unrelated window change re-shuffles the band. The symptom
 * is the one users report as "the HUD only shows after I alt-tab" — the notch
 * is drawn, it is just underneath the game.
 *
 * Re-asserting [User32.SetWindowPos] with `HWND_TOPMOST` on a slow reconcile
 * tick is the Windows twin of [MacOverlayWindowPolicy]'s level reconcile. The
 * call carries `SWP_NOACTIVATE` (never steal focus from the game),
 * `SWP_NOMOVE | SWP_NOSIZE` (AWT owns placement) and `SWP_ASYNCWINDOWPOS` (the
 * native call never blocks this worker on the window's thread). The tick
 * tightens to [GamingTickMs] while a game session is live so the HUD is back
 * on top within a frame or two of the game re-raising itself.
 *
 * This cannot beat a true exclusive-fullscreen swap chain — Windows stops
 * compositing every other window there, so no ordinary window can draw over
 * it. Games running borderless (the common modern case, including Windows'
 * "fullscreen optimizations") are covered, which is exactly where a topmost
 * window is supposed to work.
 *
 * Off Windows this installs nothing. Fail-soft: an unresolvable HWND or a
 * rejected `SetWindowPos` leaves AWT's original behaviour in place.
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
            // The peer is normally there by the time the overlay composes; if
            // not, `currentHandle()` retries on every tick rather than giving up.
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

    /**
     * Re-targets the policy. [alwaysOnTop] mirrors the Behavior toggle;
     * [gaming] is the raw detector session (the HUD, or the notch itself while
     * a game owns the screen) and only changes how often the band is
     * re-asserted.
     */
    fun update(alwaysOnTop: Boolean, gaming: Boolean) {
        wantedAlwaysOnTop = alwaysOnTop
        this.gaming = gaming
        scope.launch { runCatching { apply() } }
    }

    private fun apply() {
        val wanted = wantedAlwaysOnTop
        // With always-on-top off, leave AWT's z-order alone unless this policy
        // previously raised the window itself. Re-asserting NOTOPMOST every
        // tick would keep shoving the notch below every other window.
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

        // `SetWindowPos` insert-after pseudo-handles (-1 = topmost, -2 = not).
        // Deliberately `Pointer(-1L)`, not `Pointer.createConstant(-1)`: the
        // latter's special constant representation reaches the native call as
        // an invalid handle (SetWindowPos fails with ERROR_INVALID_WINDOW_HANDLE
        // 1400), which is exactly the silent no-op this policy exists to avoid.
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
