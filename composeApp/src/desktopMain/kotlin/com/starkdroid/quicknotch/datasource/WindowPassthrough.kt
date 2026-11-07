package com.starkdroid.quicknotch.datasource

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.MouseInfo
import java.awt.Point
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
 * Clips input to the drawn island: Windows `SetWindowRgn`, macOS cursor-gated `setIgnoresMouseEvents`.
 * The overlay is one fixed rectangle, so without this the transparent margin swallows clicks.
 */
internal class WindowPassthrough private constructor(
    private val impl: WindowPassthroughPlatform
) : AutoCloseable {

    /**
     * The island's touch target in window pixels, or null for "the whole window is interactive".
     */
    @Volatile
    private var islandRect: Rectangle? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Windows dispatches `SetWindowRgn` on the EDT; macOS must not, because an EDT to main-queue
     * `dispatch_sync` at 33 Hz can deadlock both threads.
     */
    private val applyOnEventThread = HostPlatform.isWindows

    init {
        scope.launch {
            while (isActive) {
                try {
                    val rect = islandRect
                    if (applyOnEventThread) {
                        SwingUtilities.invokeLater { impl.apply(rect) }
                    } else {
                        // Off-EDT: cursor math and the dylib's main-queue dispatch are thread-safe here.
                        impl.apply(rect)
                    }
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
        /** Attaches the platform implementation, or null when none is available; never throws. */
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

/** Per-OS strategy for clipping the window to [rect] (window pixels); null means the whole window. */
internal interface WindowPassthroughPlatform {
    fun apply(rect: Rectangle?)
    fun close()
}

/**
 * The AWT peer is behind module encapsulation; read it by reflection so the real type never
 * leaks into compilation.
 */
internal fun windowPeer(window: Window): Any? {
    return try {
        // Component.getPeer() is package-private; read the private `peer` field (needs --add-opens).
        val field = java.awt.Component::class.java.getDeclaredField("peer")
        field.isAccessible = true
        field.get(window)
    } catch (_: Throwable) {
        null
    }
}

/** Walks a peer class hierarchy for the given native-handle accessor. */
internal fun resolvePeerLong(peer: Any?, methodName: String): Long? {
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
 * macOS `CPlatformWindow` keeps its NSWindow in the protected `CFRetainedResource.ptr` field,
 * with no getter on JDK 17; resolve it via `getPlatformWindow` and reflection.
 */
internal fun resolveMacWindowPtr(window: Window): Long? {
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

// Windows: SetWindowRgn clips both painting and hit-testing.

/**
 * SetWindowRgn hands the region to the window, so a fresh CreateRectRgn
 * per tick is safe and a null region makes the window interactive again.
 */
private class Win32Region(private val window: Window) : WindowPassthroughPlatform {
    private val hwnd = resolvePeerLong(windowPeer(window), "getHWnd").also {
        if (it == null || it == 0L) {
            System.err.println("QuickNotch: Windows overlay passthrough off (HWND resolve failed)")
        }
    }

    init {
        val h = hwnd
        if (h != null && h != 0L) {
            try {
                // WS_EX_TOOLWINDOW hides it from the taskbar and Alt+Tab;
                // WS_EX_NOACTIVATE keeps a click on the island from yanking
                // focus off whatever the user was typing into.
                val gwlExStyle = -20
                val wsExToolWindow = 0x00000080
                val wsExNoActivate = 0x08000000
                val style = User32.INSTANCE.GetWindowLong(WinDef.HWND(Pointer(h)), gwlExStyle)
                User32.INSTANCE.SetWindowLong(
                    WinDef.HWND(Pointer(h)),
                    gwlExStyle,
                    style or wsExToolWindow or wsExNoActivate
                )
            } catch (t: Throwable) {
                System.err.println("QuickNotch: Windows overlay taskbar removal failed: ${t.message}")
            }
        }
    }

    private var lastRect: Rectangle? = null

    override fun apply(rect: Rectangle?) {
        val h = hwnd ?: return
        if (rect == lastRect) return
        lastRect = rect?.clone() as Rectangle?
        // Rect comes in the window's *physical* pixels (Compose px = dp ×
        // density), exactly what the Win32 region wants on a DPI-aware
        // process. A couple of points of slack on every side keep a click
        // that grazes the pill's edge on the island instead of dropping
        // through to whatever sits underneath.
        val pad = 2
        val rgn = if (rect == null || rect.width <= 0 || rect.height <= 0) {
            null
        } else {
            GDI32.INSTANCE.CreateRectRgn(
                rect.x - pad,
                rect.y - pad,
                rect.x + rect.width + pad,
                rect.y + rect.height + pad
            )
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

// macOS: whole-window mouse transparency, cursor-gated.

/**
 * ignoresMouseEvents has no per-region form, so the window stays transparent
 * unless the real cursor is over the island. The cursor check uses the
 * bridge's CG origin.
 */
private class MacClickThrough(private val window: Window) : WindowPassthroughPlatform {

    // Owns the delayed synthetic-enter hops (see setIgnoring).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    private val unavailable by lazy { nsWindowPtr == null || !MacAppKit.windowResponds(nsWindowPtr!!) }
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
        scope.cancel()
        val ptr = nsWindowPtr ?: return
        if (unavailable) return
        try {
            setIgnoring(ptr, false)
        } catch (_: Throwable) {
        }
    }

    /**
     * The window's top-left in real CG screen coordinates, or AWT's cached
     * location when the native side can't answer (bridge missing, first frames).
     */
    private fun realOrigin(): Point {
        val ptr = nsWindowPtr
        val cg = if (ptr != null) MacAppKit.windowCgOrigin(ptr) else null
        return cg ?: window.locationOnScreen
    }

    private fun cursorOver(rect: Rectangle): Boolean {
        val cursor = try {
            MouseInfo.getPointerInfo()?.location
        } catch (_: Throwable) {
            return false
        } ?: return false
        val origin = try {
            realOrigin()
        } catch (_: Throwable) {
            return false
        }
        // Convert the island rect from physical pixels to AWT points; the
        // origin comes from the bridge, not locationOnScreen.
        val scale = try {
            window.graphicsConfiguration.defaultTransform.scaleX.toFloat()
        } catch (_: Throwable) {
            1f
        }.coerceAtLeast(0.25f)
        val x = cursor.x - origin.x - window.insets.left
        val y = cursor.y - origin.y - window.insets.top
        val pad = 4
        val left = (rect.x / scale).toInt() - pad
        val top = (rect.y / scale).toInt() - pad
        val right = ((rect.x + rect.width) / scale).toInt() + pad
        val bottom = ((rect.y + rect.height) / scale).toInt() + pad
        return x >= left && x <= right && y >= top && y <= bottom
    }

    private fun setIgnoring(ptr: Long, ignore: Boolean) {
        if (ignore == ignoring) return
        if (!MacAppKit.windowResponds(ptr)) {
            // Never trust the peer friendship: a non-NSWindow pointer must not
            // receive the setter (an unrecognised selector aborts the JVM).
            if (!reported) {
                reported = true
                System.err.println("QuickNotch: macOS overlay passthrough off (pointer is not a mouse-event window)")
            }
            return
        }
        MacAppKit.setIgnoresMouseEvents(ptr, ignore)
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
            val origin = realOrigin()
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
            // AWT events belong on the event thread; everything else in this
            // class deliberately runs off it. The synthetic enter is rare (only
            // on a mouse-transparency re-arm), so the hop costs nothing.
            SwingUtilities.invokeLater { window.dispatchEvent(event) }
        } catch (_: Throwable) {
        }
    }
}
