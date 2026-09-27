package com.starkdroid.quicknotch.datasource

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.starkdroid.quicknotch.media.NativeResources
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Makes the transparent margin of the fixed-footprint overlay window
 * click-through, so only the drawn island is a touch target and the desktop /
 * foreground apps right around the collapsed pill keep receiving input.
 *
 * The overlay window is one fixed (expanded) rectangle for its whole life
 * (see `NotchOverlayWindow`): that footprint is what swallowed clicks around
 * the pill. Because AWT has no per-region mouse hit-testing, each platform
 * clips input with its own native mechanism driven by a 30 ms poll:
 *
 *  • Windows — `SetWindowRgn`: clipping the native window's hit-test region to
 *    exactly the island's rectangle makes Win32 skip clicks outside it, so they
 *    fall through to whatever is underneath for free. The region follows the
 *    island while it grows and shrinks, so the whole drag-into-the-dock area
 *    stays live during the open animation.
 *  • macOS — `[NSWindow setIgnoresMouseEvents:]`: there is no region hit-test,
 *    so the whole window is made mouse-transparent unless the real cursor
 *    (`MouseInfo`, which keeps working even on a transparent window) is over
 *    the island's rectangle. Re-arming also synthesizes a MOUSE_MOVED so
 *    Compose's hover machinery learns about a cursor that was already there.
 *
 * Both paths fail soft: if the native handle cannot be resolved (platform
 * surprise, JDK internals moving, a security manager) the window simply keeps
 * its old whole-footprint behaviour and the margin stays click-swallowing —
 * the notch itself always works.
 */
internal class WindowPassthrough private constructor(
    private val impl: WindowPassthroughPlatform
) : AutoCloseable {

    /**
     * The island's touch-target rectangle in *window* (client) pixels, or null
     * for "the entire window is interactive" (used until the overlay's hover
     * box has been measured, and while the island covers the window).
     */
    @Volatile
    private var islandRect: Rectangle? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            while (isActive) {
                try {
                    val rect = islandRect
                    // AppKit (macOS) and the owning thread (Windows region)
                    // both require native window calls on the event thread:
                    // Compose Desktop renders on the EDT, so dispatch there.
                    SwingUtilities.invokeLater { impl.apply(rect) }
                } catch (_: Throwable) {
                    // Fail soft: a single bad tick must never take the overlay down.
                }
                delay(30)
            }
        }
    }

    fun update(rect: Rectangle?) {
        islandRect = rect
    }

    override fun close() {
        scope.cancel()
        try {
            impl.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        /**
         * Attaches the right platform implementation to [window], or returns
         * null when the platform has no passthrough mechanism available. Never
         * throws: probing native internals must not take the app down.
         */
        fun install(window: Window): WindowPassthrough? {
            return try {
                when {
                    HostPlatform.isWindows -> WindowPassthrough(Win32Region(window))
                    HostPlatform.isMac -> WindowPassthrough(MacClickThrough(window))
                    else -> null
                }
            } catch (t: Throwable) {
                System.err.println("QuickNotch: overlay passthrough unavailable: ${t.message}")
                null
            }
        }
    }
}

/**
 * Per-OS strategy for clipping the window's interactive area to [rect]
 * (window-client pixels); null means the whole window is interactive.
 */
internal interface WindowPassthroughPlatform {
    fun apply(rect: Rectangle?)
    fun close()
}

/**
 * The AWT peer behind a [Window] is hidden behind module encapsulation; look it
 * up with reflection so the actual peer type never leaks into compilation.
 */
private fun windowPeer(window: Window): Any? {
    return try {
        // Component.getPeer() is package-private and there is no public
        // accessor, so read the private `peer` field (+ --add-opens below).
        val field = java.awt.Component::class.java.getDeclaredField("peer")
        field.isAccessible = true
        field.get(window)
    } catch (_: Throwable) {
        null
    }
}

/** Walks a peer class hierarchy for the given native-handle accessor. */
private fun resolvePeerLong(peer: Any?, methodName: String): Long? {
    var ptr: Long? = null
    try {
        var cls: Class<*>? = peer?.javaClass
        while (cls != null) {
            try {
                val method = cls.getDeclaredMethod(methodName)
                method.isAccessible = true
                val value = method.invoke(peer)
                ptr = (value as? Long) ?: (value as? Number)?.toLong()
                break
            } catch (_: NoSuchMethodException) {
                cls = cls.superclass
            }
        }
    } catch (_: Throwable) {
    }
    return ptr
}

/**
 * The macOS AWT peer (`sun.lwawt.macosx.CPlatformWindow`) holds its NSWindow
 * in the protected `CFRetainedResource.ptr` field — there is no public
 * `getNSWindowPtr` on JDK 17. Resolve it via `peer.getPlatformWindow()` and
 * read that field by reflection.
 */
private fun resolveMacWindowPtr(window: Window): Long? {
    val peer = windowPeer(window) ?: return null
    val platform = try {
        peer.javaClass.getMethod("getPlatformWindow").invoke(peer)
    } catch (_: Throwable) {
        null
    } ?: return null
    try {
        var cls: Class<*>? = platform.javaClass
        while (cls != null) {
            try {
                val field = cls.getDeclaredField("ptr")
                field.isAccessible = true
                if (field.type == java.lang.Long.TYPE) {
                    return field.getLong(platform)
                }
            } catch (_: NoSuchFieldException) {
            }
            cls = cls.superclass
        }
    } catch (_: Throwable) {
    }
    return null
}

// ── Windows: SetWindowRgn clips both painting and hit-testing ─────────────────

/**
 * Windows impl. [SetWindowRgn] hands the region to the window (which frees it
 * when replaced), so passing a fresh `CreateRectRgn` every tick is safe and the
 * OS cleans up the previous one. A null region restores a fully interactive
 * window.
 */
private class Win32Region(private val window: Window) : WindowPassthroughPlatform {
    private val hwnd = resolvePeerLong(windowPeer(window), "getHWnd").also {
        if (it == null || it == 0L) {
            System.err.println("QuickNotch: Windows overlay passthrough off (HWND resolve failed)")
        }
    }

    override fun apply(rect: Rectangle?) {
        val h = hwnd ?: return
        // Rect comes in the window's *physical* pixels (Compose density on
        // desktop is the window DPI scale), exactly what the Win32 region wants.
        // An empty rect behaves like no region at all.
        val rgn = if (rect == null || rect.width <= 0 || rect.height <= 0) {
            null
        } else {
            GDI32.INSTANCE.CreateRectRgn(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
        }
        User32.INSTANCE.SetWindowRgn(WinDef.HWND(Pointer(h)), rgn, true)
    }

    override fun close() {
        val h = hwnd ?: return
        try {
            User32.INSTANCE.SetWindowRgn(WinDef.HWND(Pointer(h)), null, true)
        } catch (_: Throwable) {
        }
    }
}

// ── macOS: whole-window mouse transparency, cursor-gated ──────────────────────

/**
 * macOS impl. `ignoresMouseEvents` has no per-region variant, so the window is
 * kept mouse-transparent unless the real cursor sits over the island's
 * rectangle; the toggle is a simple reconcile against `MouseInfo` each tick.
 *
 * The actual AppKit toggle runs through the compiled `passthrough_bridge.m`
 * dylib (`quicknotch_set_ignores_mouse_events`, loaded via JNA): it flips the
 * property on AppKit's main queue from a plain C symbol, which is far more
 * reliable than driving objc_msgSend straight from JVM land (void-return /
 * BOOL-arg sends crash with SIGTRAP on Apple silicon). Missing native bridge =
 * fail soft.
 */
private class MacClickThrough(private val window: Window) : WindowPassthroughPlatform {
    private val nsWindowPtr: Long? = run {
        val ptr = resolveMacWindowPtr(window)
        if (ptr == null || ptr == 0L) {
            System.err.println("QuickNotch: macOS overlay passthrough off (NSWindow resolve failed)")
            null
        } else {
            System.err.println("QuickNotch: macOS overlay passthrough armed (NSWindow 0x${ptr.toString(16)})")
            ptr
        }
    }
    private val bridge: NativeLibrary? = NativeResources
        .extract("macos/passthrough-bridge.dylib", ".dylib")
        ?.let { lib ->
            try {
                NativeLibrary.getInstance(lib.absolutePath)
            } catch (_: Throwable) {
                null
            }
        }
    private val setIgnoreFn: Function? = bridge?.getFunction("quicknotch_set_ignores_mouse_events")
    private val respondsFn: Function? = runCatching { bridge?.getFunction("quicknotch_window_responds") }.getOrNull()
    private val unavailable = bridge == null || setIgnoreFn == null
    private var reported = false

    /** Log the fail-soft reason once; never spam a 30 ms poll. */
    private fun reportOnce() {
        if (!reported) {
            reported = true
            System.err.println("QuickNotch: macOS overlay passthrough off (native bridge unavailable)")
        }
    }

    /** The last value we sent to `setIgnoresMouseEvents:` (avoids redundant calls). */
    private var ignoring = false

    override fun apply(rect: Rectangle?) {
        val ptr = nsWindowPtr ?: return
        if (unavailable) {
            reportOnce()
            return
        }
        val interactive = rect == null || cursorOver(rect)
        setIgnoring(ptr, !interactive)
    }

    override fun close() {
        val ptr = nsWindowPtr ?: return
        if (unavailable) return
        try {
            setIgnoring(ptr, false)
        } catch (_: Throwable) {
        }
    }

    private fun cursorOver(rect: Rectangle): Boolean {
        val cursor = try {
            MouseInfo.getPointerInfo()?.location
        } catch (_: Throwable) {
            return false
        } ?: return false
        val origin = try {
            window.locationOnScreen
        } catch (_: Throwable) {
            return false
        }
        // Content origin = window origin + frame insets (macOS keeps shadow
        // insets even for undecorated windows).
        val x = cursor.x - origin.x - window.insets.left
        val y = cursor.y - origin.y - window.insets.top
        val pad = 4
        return x >= rect.x - pad && x <= rect.x + rect.width + pad &&
            y >= rect.y - pad && y <= rect.y + rect.height + pad
    }

    private fun setIgnoring(ptr: Long, ignore: Boolean) {
        if (ignore == ignoring) return
        val respond = try {
            respondsFn?.invokeInt(arrayOf(ptr)) ?: 1
        } catch (_: Throwable) {
            -1
        }
        if (respond == 0) {
            // Never trust the peer friendship: a non-NSWindow pointer must not
            // receive the setter (an unrecognised selector aborts the JVM).
            if (!reported) {
                reported = true
                System.err.println("QuickNotch: macOS overlay passthrough off (pointer is not a mouse-event window)")
            }
            return
        }
        setIgnoreFn?.invoke(java.lang.Void::class.java, arrayOf(ptr, if (ignore) 1 else 0))
        ignoring = ignore
        if (!ignore) {
            // The cursor is already over the (now interactive) island, but
            // Compose has only ever seen a mouse-transparent window: synthesise
            // a MOUSE_MOVED so its hover machinery sees the "enter".
            synthesizeEnter()
        }
    }

    private fun synthesizeEnter() {
        try {
            val cursor = MouseInfo.getPointerInfo()?.location ?: return
            val origin = window.locationOnScreen
            val event = MouseEvent(
                window,
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                cursor.x - origin.x,
                cursor.y - origin.y,
                0,
                false
            )
            window.dispatchEvent(event)
        } catch (_: Throwable) {
        }
    }
}