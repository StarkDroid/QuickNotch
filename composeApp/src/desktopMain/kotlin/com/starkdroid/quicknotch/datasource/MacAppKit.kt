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

    /** One step below normal: every app window covers it, and unlike level 0
     *  the menu bar does not clamp it back below its own strip. */
    const val LEVEL_BELOW_NORMAL = -1
    /** NSStatusWindowLevel: the band the menu bar occupies — above every normal
     *  app window and the menu bar itself, still below alert/modal levels. */
    const val LEVEL_STATUS_BAR = 25

    /** canJoinAllSpaces | stationary | ignoresCycle. */
    const val COLLECTION_DESKTOP: Long =
        (1L shl 0) or (1L shl 4) or (1L shl 12)

    /** [COLLECTION_DESKTOP] plus fullScreenAuxiliary: rides over full-screen apps. */
    const val COLLECTION_NOTCH: Long =
        COLLECTION_DESKTOP or (1L shl 8)

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

    /**
     * The window's top-left in *real* CG screen coordinates (top-left origin,
     * points). AWT's `locationOnScreen` reports its own cached virtual position
     * (AWT y=0 is 30pt below the true top), which drifts from the actual
     * NSWindow frame once the above-menu-bar nudge moves it directly in Cocoa.
     * Passthrough's cursor-over math needs this real origin. Null when the
     * native side can't answer (bridge missing, window not mapped).
     */
    private val originXBuf: Memory by lazy { Memory(8) }
    private val originYBuf: Memory by lazy { Memory(8) }

    fun windowCgOrigin(ptr: Long): Point? {
        val getter = fn("quicknotch_get_window_cg_origin") ?: return null
        return try {
            if (getter.invokeInt(arrayOf(ptr, originXBuf, originYBuf)) == 1) {
                Point(originXBuf.getDouble(0).toInt(), originYBuf.getDouble(0).toInt())
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

    /**
     * Backdrop blur behind the island, via the island-sized helper window
     * (created, positioned and torn down inside the bridge; the compositor
     * radius + effect view inside it both follow `frame.intensity`).
     * Fire-and-forget on AppKit's main queue; a no-op without the bridge.
     */
    fun setContentBlur(ptr: Long, enabled: Boolean, frame: BlurFrame) {
        call(
            "quicknotch_set_content_blur",
            ptr,
            if (enabled) 1 else 0,
            frame.w,
            frame.h,
            frame.cornerRadius,
            if (frame.roundTop) 1 else 0,
            frame.intensity
        )
    }

    fun activateApp() {
        call("quicknotch_activate_app")
    }
}

/**
 * macOS-only per-overlay controller that gives the notch its above-menu-bar
 * home: raised above the menu bar, flush against the true top of the screen
 * (AWT cannot reach that position — it clamps placement to the visible area),
 * joining every Space and surviving full-screen apps.
 *
 * AWT's z-order bookkeeping resets window levels whenever it reorders sibling
 * windows, and each Compose placement re-parks the window 30pt low, so the
 * policy keeps a slow reconcile loop that re-asserts level + frame every tick.
 * The "always on top" toggle maps to level and collection behaviour only; the
 * frame never changes:
 *
 *  • on  — NSStatusWindowLevel (25, above the menu bar), joining every Space
 *          and surviving full-screen apps.
 *  • off — one level below normal (-1), the same frame, no full-screen band,
 *          so regular windows can cover the island again.
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
        val onTop = wantedAlwaysOnTop
        // Off gives up the full-screen band as well as the level: an ordinary
        // window should not keep riding above a game or a full-screen video,
        // which is what fullScreenAuxiliary is for.
        MacAppKit.setCollectionBehavior(
            ptr,
            if (onTop) MacAppKit.COLLECTION_NOTCH else MacAppKit.COLLECTION_DESKTOP
        )
        MacAppKit.setWindowLevel(
            ptr,
            if (onTop) MacAppKit.LEVEL_STATUS_BAR else MacAppKit.LEVEL_BELOW_NORMAL
        )
        // The frame is identical in both states: the toggle changes stacking,
        // never position. Level -1 sits under every app window but is not
        // clamped by the menu bar the way level 0 is, so the notch keeps its
        // top-edge home while a foreground window can cover it.
        MacAppKit.setFrameTopGap(ptr, wantedEdgeGapDp.toDouble())
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
 * icon + app switcher) and is brought to the front **once** so launching/
 * settings feels like a real app; closing Settings returns it to the quiet tray
 * agent.
 *
 * A reconcile loop re-asserts the activation *policy* every tick because AWT
 * initialises the process as a regular application and can fight a one-shot
 * flip mid-session. It deliberately never re-activates: `activateIgnoringOther
 * Apps` called on each tick would keep yanking focus back to the Settings
 * window and make every other app unclickable (the front-most app would snap
 * back to QuickNotch within one tick). Activation is therefore a one-shot
 * request consumed on the next policy pass, armed only when Settings transitions
 * to visible.
 *
 * No-op off macOS. Mirrors the "the app belongs in the Dock only when the
 * Settings page is open" requirement.
 */
internal object MacDockPresence {

    @Volatile private var settingsVisible = false
    @Volatile private var running = false

    /**
     * One-shot focus request: armed when Settings opens, consumed by the next
     * [applyPolicy] pass. Kept false during the steady state so the reconcile
     * loop never steals focus from another application.
     */
    @Volatile private var pendingActivate = false
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
        MacAppKit.setActivationPolicy(if (settingsVisible) MacAppKit.ACTIVATION_REGULAR else MacAppKit.ACTIVATION_ACCESSORY)
        if (settingsVisible && pendingActivate) {
            // Front the app exactly once per open transition. An accessory app
            // can't surface its (now regular) Settings window on its own, but
            // re-firing this every tick would make other apps unclickable.
            pendingActivate = false
            MacAppKit.activateApp()
        }
    }

    fun setSettingsVisible(visible: Boolean) {
        settingsVisible = visible
        // Only a transition into "visible" asks for a single activation; staying
        // open never re-activates (that's what blocked focus on other apps).
        if (visible) pendingActivate = true
        scope?.launch { applyPolicy() }
    }

    fun stop() {
        running = false
        scope?.cancel()
        scope = null
    }

    private const val DockTickMs = 700L
}
