package com.starkdroid.quicknotch.datasource

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.starkdroid.quicknotch.media.NativeResources
import java.awt.Point
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Thin JVM front for the compiled `passthrough_bridge.m` dylib, shared by the
 * overlay's click-through (see `WindowPassthrough`), the notch's window policy
 * (above-menu-bar placement + Spaces behaviour) and the Dock-presence toggle.
 *
 * The dylib exists only when the project was built on a macOS host; on other
 * hosts every call here is a silent no-op so the Windows build degrades the same
 * way the click-through already does (fail soft, never throws).
 *
 * All computed constants mirror the AppKit headers. Window/application geometry
 * reads go through the bridge's main-thread-safe helpers so callers never have
 * to care which JVM thread they run on.
 */
internal object MacAppKit {

    // ── AppKit constants (kept here so the JVM side never hardcodes magic) ──────

    /** NSAccessory equivalent: no Dock icon, no app menu bar (pure agent). */
    const val ACTIVATION_REGULAR = 0
    /** Accessory agent: the notch's resting state. */
    const val ACTIVATION_ACCESSORY = 1

    /** NSNormalWindowLevel. */
    const val LEVEL_NORMAL = 0
    /** NSStatusWindowLevel: the band the menu bar occupies — above every normal
     *  app window and the menu bar itself, still below alert/modal levels. */
    const val LEVEL_STATUS_BAR = 25
    /** NSFloatingWindowLevel (what AWT's alwaysOnTop reaches). */
    const val LEVEL_FLOATING = 3

    /** canJoinAllSpaces | stationary | fullScreenAuxiliary | ignoresCycle. */
    const val COLLECTION_NOTCH: Long =
        (1L shl 0) or (1L shl 4) or (1L shl 8) or (1L shl 12)

    private val bridge: NativeLibrary? by lazy {
        runCatching {
            NativeResources.extract("macos/passthrough-bridge.dylib", ".dylib")
                ?.let { NativeLibrary.getInstance(it.absolutePath) }
        }.getOrNull()
    }

    private val functions = ConcurrentHashMap<String, Function?>()

    private fun fn(name: String): Function? = functions.computeIfAbsent(name) {
        bridge?.let { lib -> runCatching { lib.getFunction(it) }.getOrNull() }
    }

    private fun call(name: String, vararg args: Any?) {
        try {
            fn(name)?.invoke(java.lang.Void::class.java, arrayOf(*args))
        } catch (_: Throwable) {
            // Fail soft: a single bad message must never take the overlay down.
        }
    }

    /** "Is this pointer really an NSWindow that understands our setters?" */
    fun windowResponds(ptr: Long): Boolean =
        (fn("quicknotch_window_responds")?.invokeInt(arrayOf(ptr)) ?: 1) == 1

    fun setIgnoresMouseEvents(ptr: Long, ignore: Boolean) {
        call("quicknotch_set_ignores_mouse_events", ptr, if (ignore) 1 else 0)
    }

    fun setWindowLevel(ptr: Long, level: Int) {
        call("quicknotch_set_window_level", ptr, level)
    }

    fun setCollectionBehavior(ptr: Long, behavior: Long) {
        call("quicknotch_set_collection_behavior", ptr, behavior)
    }

    fun setFrameTopGap(ptr: Long, topGap: Double) {
        call("quicknotch_set_frame_top_gap", ptr, topGap)
    }

    fun menuBarHeight(ptr: Long): Double {
        return try {
            fn("quicknotch_get_menu_bar_height")?.invokeDouble(arrayOf(ptr)) ?: 0.0
        } catch (_: Throwable) {
            0.0
        }
    }

    /**
     * The window's top-left in *real* CG screen coordinates (top-left origin,
     * points). AWT's `locationOnScreen` reports its own cached virtual position
     * (AWT y=0 is 30pt below the true top), which drifts from the actual
     * NSWindow frame once the above-menu-bar nudge moves it directly in Cocoa.
     * Passthrough's cursor-over math needs this real origin. Null when the
     * native side can't answer (bridge missing, window not mapped).
     */
    fun windowCgOrigin(ptr: Long): Point? {
        val getter = fn("quicknotch_get_window_cg_origin") ?: return null
        return try {
            val x = Memory(8)
            val y = Memory(8)
            if (getter.invokeInt(arrayOf(ptr, x, y)) == 1) {
                Point(x.getDouble(0).toInt(), y.getDouble(0).toInt())
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * How far AWT's cached `locationOnScreen` strays from the window's *real*
     * CG origin (top-left origin, points). `Window.locationOnScreen` on macOS
     * reflects AWT's own virtual placement (its y=0 sits at the bottom of the
     * menu bar), which stops matching the live NSWindow frame the moment the
     * overlay policy parks the window with direct Cocoa moves. Compose's
     * `localToScreen` inherits that stale number, so screen-space hover checks
     * must be shifted by this delta. Null = no reliable real origin (bridge
     * missing / window not mapped) → callers fall back to AWT's coordinates.
     */
    fun screenOriginDelta(window: java.awt.Window): Point? {
        val ptr = resolveMacWindowPtr(window) ?: return null
        val real = windowCgOrigin(ptr) ?: return null
        val awt = try {
            window.locationOnScreen
        } catch (_: Throwable) {
            return null
        }
        return Point(real.x - awt.x, real.y - awt.y)
    }

    fun setActivationPolicy(policy: Int) {
        call("quicknotch_set_activation_policy", policy)
    }

    fun activateApp() {
        call("quicknotch_activate_app")
    }
}

/**
 * macOS-only per-overlay controller that gives the notch its Dynamic Island
 * home: raised above the menu bar, flush against the true top of the screen
 * (AWT cannot reach that position — it clamps placement to the visible area),
 * joining every Space and surviving full-screen apps.
 *
 * AWT's z-order bookkeeping resets window levels whenever it reorders sibling
 * windows, and each ComPOse placement re-parks the window 30pt low, so the
 * policy keeps a slow reconcile loop that re-asserts level + frame every tick.
 * The "always on top" toggle maps to level AND the parked vertical gap:
 *
 *  • on  — NSStatusWindowLevel (25, above the menu bar), island flush at the
 *          very top (floating styles keep their edge gap).
 *  • off — NSNormalWindowLevel (0), island parked just below the menu bar so
 *          regular windows can cover it again.
 */
internal class MacOverlayWindowPolicy private constructor(
    private val window: Window
) : AutoCloseable {

    @Volatile private var wantedAlwaysOnTop = false
    @Volatile private var wantedEdgeGapDp = 0
    private val nsWindowPtr: Long = resolveMacWindowPtr(window) ?: 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        if (nsWindowPtr != 0L && MacAppKit.windowResponds(nsWindowPtr)) {
            scope.launch {
                while (isActive) {
                    try {
                        apply()
                    } catch (_: Throwable) {
                    }
                    delay(OverlayPolicyTickMs)
                }
            }
        } else {
            System.err.println("QuickNotch: macOS overlay window policy off (NSWindow resolve failed)")
        }
    }

    /** Re-target the policy (new always-on-top state / style edge gap), applied
     *  on the next reconcile tick (≤ ~700ms) */
    fun update(alwaysOnTop: Boolean, edgeGapDp: Int) {
        wantedAlwaysOnTop = alwaysOnTop
        wantedEdgeGapDp = edgeGapDp
        scope.launch { apply() }
    }

    private fun apply() {
        val ptr = nsWindowPtr
        if (ptr == 0L) return
        MacAppKit.setCollectionBehavior(ptr, MacAppKit.COLLECTION_NOTCH)
        MacAppKit.setWindowLevel(ptr, if (wantedAlwaysOnTop) MacAppKit.LEVEL_STATUS_BAR else MacAppKit.LEVEL_NORMAL)
        // Off-state parks the island below the menu bar (AWT's default spot);
        // on-state starts the gap at the true top of the screen.
        val menuBar = if (wantedAlwaysOnTop) 0.0 else MacAppKit.menuBarHeight(ptr)
        MacAppKit.setFrameTopGap(ptr, menuBar + wantedEdgeGapDp)
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        private const val OverlayPolicyTickMs = 700L

        fun install(window: Window): MacOverlayWindowPolicy? {
            return try {
                if (!HostPlatform.isMac) null else MacOverlayWindowPolicy(window)
            } catch (t: Throwable) {
                System.err.println("QuickNotch: macOS overlay window policy unavailable: ${t.message}")
                null
            }
        }
    }
}

/**
 * macOS Dock/application presence tied to the Settings window: the notch's
 * resting state is an accessory agent — no Dock icon, no app menu bar, no
 * focus stealing, and the overlay can layer over full-screen apps. As soon as
 * the Settings window is open the app flips to a regular application (Dock
 * icon + app switcher + activation) so launching/settings feels like a real
 * app; closing Settings returns it to the quiet tray agent.
 *
 * A reconcile loop re-asserts the policy every tick because AWT initialises the
 * process as a regular application and can fight a one-shot flip mid-session.
 * No-op off macOS. Mirrors the "the app belongs in the Dock only when the
 * Settings page is open" requirement.
 */
internal object MacDockPresence {

    @Volatile private var settingsVisible = false
    @Volatile private var running = false
    private var scope: CoroutineScope? = null

    fun start() {
        if (!HostPlatform.isMac) return
        stop()
        running = true
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        active.launch {
            while (isActive && running) {
                try {
                    applyPolicy()
                } catch (_: Throwable) {
                }
                delay(DockTickMs)
            }
        }
        applyPolicy()
    }

    private fun applyPolicy() {
        if (!running) return
        val regular = settingsVisible
        MacAppKit.setActivationPolicy(if (regular) MacAppKit.ACTIVATION_REGULAR else MacAppKit.ACTIVATION_ACCESSORY)
        if (regular) {
            // An accessory app cannot bring its (now regular) window to the
            // front on its own; don't leave the Settings window buried.
            MacAppKit.activateApp()
        }
    }

    fun setSettingsVisible(visible: Boolean) {
        settingsVisible = visible
        scope?.launch { applyPolicy() }
    }

    fun stop() {
        running = false
        scope?.cancel()
        scope = null
    }

    private const val DockTickMs = 700L
}