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
 * JVM front for `passthrough_bridge.m`: click-through, window policy and Dock presence.
 * Without the dylib (a non-macOS build) every call is a silent no-op.
 */
internal object MacAppKit {

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
     * The window's top-left in real CG screen coordinates (top-left origin, points). AWT's
     * `locationOnScreen` reports a cached position whose y=0 sits 30pt below the true top.
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
     * How far AWT's cached `locationOnScreen` strays from the window's real CG origin; screen-space
     * hover checks must shift by this delta. Null means no reliable origin, fall back to AWT.
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
     * Backdrop blur via the island-sized helper window (the bridge owns it); fire-and-forget on
     * AppKit's main queue, no-op without the bridge.
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
 * macOS-only controller that parks the notch flush at the true top of the screen, above the menu bar,
 * on every Space. AWT re-parks the window 30pt low, so a 700ms loop re-asserts level and frame.
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

    /** Re-targets the policy; applied on the next reconcile tick. */
    fun update(alwaysOnTop: Boolean, edgeGapDp: Int) {
        wantedAlwaysOnTop = alwaysOnTop
        wantedEdgeGapDp = edgeGapDp
        scope.launch { apply() }
    }

    private fun apply() {
        val ptr = nsWindowPtr
        if (ptr == 0L) return
        val onTop = wantedAlwaysOnTop
        // Off drops the full-screen band too: an ordinary window should not ride above a game.
        MacAppKit.setCollectionBehavior(
            ptr,
            if (onTop) MacAppKit.COLLECTION_NOTCH else MacAppKit.COLLECTION_DESKTOP
        )
        MacAppKit.setWindowLevel(
            ptr,
            if (onTop) MacAppKit.LEVEL_STATUS_BAR else MacAppKit.LEVEL_BELOW_NORMAL
        )
        // The frame is identical in both states: the toggle changes stacking, never position.
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
 * Dock/application presence tied to the Settings window: accessory agent at rest, Regular plus a
 * single activation while Settings is open. The loop re-asserts policy only, never activation.
 */
internal object MacDockPresence {

    @Volatile private var settingsVisible = false
    @Volatile private var running = false

    /** One-shot focus request, armed when Settings opens and consumed by the next [applyPolicy]. */
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
            // Front the app once per open transition: re-firing every tick makes other apps unclickable.
            pendingActivate = false
            MacAppKit.activateApp()
        }
    }

    fun setSettingsVisible(visible: Boolean) {
        settingsVisible = visible
        // Only the transition into visible arms an activation; staying open never re-fires it.
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
