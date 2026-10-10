package com.starkdroid.quicknotch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.starkdroid.quicknotch.ui.OnboardingScreen
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.calendar.calendarUpcomingCount
import com.starkdroid.quicknotch.datasource.AppPaths
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.StartupManager
import com.starkdroid.quicknotch.datasource.MacDockPresence
import com.starkdroid.quicknotch.datasource.MacOverlayWindowPolicy
import com.starkdroid.quicknotch.datasource.WindowPassthrough
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.app_name
import com.starkdroid.quicknotch.generated.resources.menu_always_on_top
import com.starkdroid.quicknotch.generated.resources.menu_always_on_top_checked
import com.starkdroid.quicknotch.generated.resources.menu_hide_notch
import com.starkdroid.quicknotch.generated.resources.menu_quit
import com.starkdroid.quicknotch.generated.resources.menu_settings
import com.starkdroid.quicknotch.generated.resources.menu_show_notch
import com.starkdroid.quicknotch.generated.resources.onboarding_window_title
import com.starkdroid.quicknotch.generated.resources.reset_defaults
import com.starkdroid.quicknotch.generated.resources.settings_window_title
import com.starkdroid.quicknotch.media.AudioBattery
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaControllerFactory
import com.starkdroid.quicknotch.media.NativeResources
import com.starkdroid.quicknotch.media.NotificationCount
import com.starkdroid.quicknotch.weather.Weather
import com.starkdroid.quicknotch.weather.WeatherState
import com.starkdroid.quicknotch.media.SystemVolume
import com.starkdroid.quicknotch.media.SystemBattery
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.theme.QuickNotchTheme
import com.starkdroid.quicknotch.ui.FloatingNotchOverlay
import com.starkdroid.quicknotch.ui.OverlayPager
import com.starkdroid.quicknotch.ui.SettingsScreen
import com.starkdroid.quicknotch.ui.computeNotchMetrics
import com.starkdroid.quicknotch.ui.isMusicLive
import com.starkdroid.quicknotch.ui.overlayScreenLocation
import com.starkdroid.quicknotch.ui.quickViewIndicatorSlots
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.awt.Toolkit
import java.awt.event.MouseWheelListener
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** File lock held for the whole process lifetime; released on exit. */
private var instanceLockChannel: FileChannel? = null

/**
 * Lets exactly one notch exist. A second process would stack a second
 * identical overlay (same position, same level) that reads as one frozen,
 * misbehaving notch — e.g. a blurred box that ignores expand/collapse while
 * the live instance underneath keeps working. Fails open: a lock hiccup must
 * never prevent launch.
 */
private fun takeInstanceLock(): Boolean = try {
    val lockFile = AppPaths.configDir.resolve("instance.lock")
    lockFile.parentFile?.mkdirs()
    val channel = FileOutputStream(lockFile, true).channel
    if (channel.tryLock() == null) {
        channel.close()
        false
    } else {
        instanceLockChannel = channel
        true
    }
} catch (_: Exception) {
    true
}

fun main(args: Array<String>) {
    if (!takeInstanceLock()) {
        System.err.println("QuickNotch: another instance is already running, exiting")
        return
    }
    System.setProperty("apple.awt.application.name", "QuickNotch")
    if (HostPlatform.isMac) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.appearance", "system")
        // Skiko backend selection — the single biggest performance lever in the
        // app. Measured on the settings list, one scroll frame costs ~70ms of
        // process CPU on the SOFTWARE rasterizer (14fps, visibly unsmooth)
        // versus ~3.4ms on Metal (vsync-capped 60fps), so macOS now defaults to
        // METAL. The old comment here claimed Metal crashed on the transparent
        // overlay window; that was skiko 0.8.18's MetalRedrawer running on the
        // AWT EDT and tripping the macOS 26/27 main-thread assertion, fixed in
        // the pinned skiko (agents.md pitfall 28).
        //
        // Precedence: an explicit -Dskiko.renderApi / SKIKO_RENDER_API wins, then
        // the `software-render` marker file, then METAL. The marker exists
        // because a backend problem is a native crash, so it must be fixable
        // without a UI the crash may prevent from appearing.
        val marker = AppPaths.configDir.resolve("software-render")
        val backend = System.getProperty("skiko.renderApi")?.takeIf { it.isNotBlank() }
            ?: System.getenv("SKIKO_RENDER_API")?.takeIf { it.isNotBlank() }
            ?: (if (marker.exists()) "SOFTWARE" else "METAL")
        System.setProperty("skiko.renderApi", backend)
        println("QuickNotch: skiko render backend = $backend${if (marker.exists()) " (marker file)" else ""}")
        // Retro-clean bridges leaked by earlier runs (JVM death skips
        // media.stop(); the bridges now watchdog themselves, but versions
        // already in the wild do not — see NativeResources.sweepOrphanedBridges).
        NativeResources.sweepOrphanedBridges()
    }
    // A fresh manual launch (Spotlight/Dock/Finder/Launchpad) opens the Settings
    // window alongside the notch so the app's presence is obvious and the user
    // can configure it immediately. A login-item auto-start passes
    // `--login-item` (written into the launch command by StartupManager) and
    // stays quietly behind the tray instead — nobody asked for settings at boot.
    val loginItemLaunch = args.contains(StartupManager.LAUNCHED_AT_LOGIN_ARG)

    application {
        val repository = remember { FavoritesRepository() }
        val config by repository.config.collectAsState()
        val favorites by repository.favorites.collectAsState()

        // One system-wide media feed for the whole app: the tray, the overlay
        // and the settings window all observe the same session.
        val media = remember { MediaControllerFactory.create() }
        DisposableEffect(Unit) {
            media.start()
            SystemVolume.start()
            AudioBattery.start()
            SystemBattery.start()
            NotificationCount.start()
            // Calendar is opt-in and file-backed: with no token on disk this
            // returns immediately, so an unconnected user pays nothing.
            GoogleCalendar.start()
            // Weather locates itself over IP and caches the day to disk, so a
            // cold start paints the last known forecast on the first frame.
            // A manual place from settings seeds the position instead.
            Weather.start(repository.config.value.widgets.weatherPlace)
            onDispose {
                media.stop()
                SystemVolume.stop()
                AudioBattery.stop()
                SystemBattery.stop()
                NotificationCount.stop()
                Weather.stop()
            }
        }

        // Derived distinctly so the window scaffolding reacts only to the
        // flow actually finishing, not to every config write on the way.
        val onboardingDone by remember {
            repository.config.map { it.onboardingDone }.distinctUntilChanged()
        }.collectAsState(initial = repository.config.value.onboardingDone)

        var showSettings by remember { mutableStateOf(!loginItemLaunch) }
        var isNotchVisible by remember { mutableStateOf(true) }
        var isExpanded by remember { mutableStateOf(false) }

        // Flip the Dock/app presence in-step with the Settings window: the
        // first Settings open forces activation (an accessory agent can't bring
        // a regular window forward on its own), and closing it returns the app
        // to its quiet agent state.
        // Onboarding counts as "settings open": the user is actively
        // configuring, so the Dock presence and activation apply while it runs.
        LaunchedEffect(showSettings, onboardingDone) {
            MacDockPresence.setSettingsVisible(showSettings || !onboardingDone)
        }

        // Manual weather place picked in settings replaces the IP position for
        // the poller (cleared back to null = re-locate automatically). Keyed on
        // the place itself, and the controller dedupes re-emits, so unrelated
        // config writes never cost a refetch.
        LaunchedEffect(config.widgets.weatherPlace) {
            Weather.setManualPlace(config.widgets.weatherPlace)
        }

        // Dock presence is a function of the Settings window, never of the
        // notch: the resting QuickNotch is a silent agent (no Dock icon, no app
        // menu bar — the overlay layers over full-screen apps), and the Dock
        // icon + app activation appear only while the Settings page is open.
        // Started from a DisposableEffect(Unit), NOT the composition body: the
        // body re-executes on every media/volume/battery tick, and a raw call
        // here re-armed the one-shot activation on each recomposition — the app
        // then yanked focus back to itself ~every second while music played.
        DisposableEffect(Unit) {
            MacDockPresence.start()
            onDispose { MacDockPresence.stop() }
        }
        val appIcon = remember { loadQuickNotchIcon() }
        Tray(
            icon = BitmapPainter(appIcon),
            tooltip = stringResource(Res.string.app_name),
            menu = {
                Item(stringResource(Res.string.menu_settings), onClick = { showSettings = true })
                Item(
                    if (isNotchVisible) stringResource(Res.string.menu_hide_notch)
                    else stringResource(Res.string.menu_show_notch),
                    onClick = { isNotchVisible = !isNotchVisible }
                )
                Item(
                    if (config.alwaysOnTop) stringResource(Res.string.menu_always_on_top_checked)
                    else stringResource(Res.string.menu_always_on_top),
                    onClick = {
                        repository.updateConfig { it.copy(alwaysOnTop = !it.alwaysOnTop) }
                    }
                )
                Separator()
                Item(stringResource(Res.string.reset_defaults), onClick = { repository.resetToDefaults() })
                Separator()
                Item(stringResource(Res.string.menu_quit), onClick = ::exitApplication)
            }
        )

        if (isNotchVisible && onboardingDone) {
            NotchOverlayWindow(
                repository = repository,
                media = media,
                appIcon = appIcon,
                isExpanded = isExpanded,
                onExpandedChange = { isExpanded = it }
            )
        }

        // The onboarding flow lives in this same window: first-run users open
        // straight into it (even at login launch -- a fresh install has no
        // login item yet), and finishing hands the window to the tabbed
        // settings while the notch, which did not exist during onboarding,
        // appears for the first time.
        if (showSettings || !onboardingDone) {
            val settingsState = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                size = DpSize(880.dp, 660.dp)
            )
            Window(
                onCloseRequest = {
                    // Closing the window mid-onboarding finishes the flow: the
                    // notch must exist no matter what, and the config the user
                    // already touched stays.
                    showSettings = false
                    if (!onboardingDone) repository.updateConfig { it.copy(onboardingDone = true) }
                },
                state = settingsState,
                title = stringResource(
                    if (onboardingDone) Res.string.settings_window_title
                    else Res.string.onboarding_window_title
                ),
                icon = BitmapPainter(appIcon),
                resizable = true
            ) {
                val settingsWindow = window
                DisposableEffect(settingsWindow) {
                    val listener = object : WindowFocusListener {
                        override fun windowGainedFocus(e: WindowEvent?) {
                            // The user just came back from System Settings
                            // (possibly after granting Full Disk Access): re-read
                            // the notification store right now instead of waiting
                            // for the next 3s poll tick, so the helper row swaps
                            // to "Send test notification" as soon as access lands.
                            NotificationCount.poke()
                        }
                        override fun windowLostFocus(e: WindowEvent?) {
                        }
                    }
                    settingsWindow.addWindowFocusListener(listener)
                    onDispose { settingsWindow.removeWindowFocusListener(listener) }
                }
                QuickNotchTheme(darkTheme = true) {
                    Crossfade(
                        targetState = onboardingDone,
                        animationSpec = tween(320),
                        label = "onboarding-to-settings"
                    ) { done ->
                        if (done) {
                            SettingsScreen(
                                repository = repository,
                                media = media,
                                onClose = { showSettings = false }
                            )
                        } else {
                            OnboardingScreen(repository = repository)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hosts the transparent overlay window and pins it to the top of the screen.
 *
 * The native window adopts a single fixed footprint — the expanded dock size —
 * and never resizes while the notch opens or collapses. On macOS a transparent
 * AWT window that resizes flashes white / skips frames for a frame (a known
 * Compose Desktop + Skiko issue), which reads as a blink; keeping the window
 * size stable and drawing the growing/shrinking island inside it eliminates
 * that entirely. The trade-off: the fixed window is wider/taller than the
 * collapsed pill, so its transparent margin swallows clicks on the desktop
 * directly around the pill until the notch expands.
 *
 * Size and position are both written to [androidx.compose.ui.window.WindowState]
 * within a single effect so Compose applies them atomically in one pass — this is
 * what keeps the notch perfectly still while it grows and shrinks. The effect
 * re-runs only when the style or the target footprint changes (favourites count,
 * the widened now-playing page), never in response to the window's own live
 * dimensions, so there is no feedback loop and no second corrective placement.
 */
@Composable
private fun NotchOverlayWindow(
    repository: FavoritesRepository,
    media: MediaController,
    appIcon: ImageBitmap,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val config by repository.config.collectAsState()
    val liveSession by media.session.collectAsState()
    val musicLive = isMusicLive(config, liveSession)
    val volumePercent by SystemVolume.percent.collectAsState()
    val volumeMuted by SystemVolume.muted.collectAsState()
    val battery by AudioBattery.info.collectAsState()
    val deviceBattery by SystemBattery.state.collectAsState()
    val notificationCount by NotificationCount.count.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val calendarState by GoogleCalendar.state.collectAsState()
    val calendarTick by GoogleCalendar.minuteTick.collectAsState()
    // The calendar page width is only reserved while the widget is switched
    // on *and* connected — the same rule the overlay's pager follows, so a
    // toggled-off calendar neither pages nor widens the window.
    val calendarConnected =
        config.widgets.showCalendar && calendarState is CalendarState.Connected
    // The native window and the overlay content must size the quickview
    // identically, or the indicator slots (device + laptop battery + the
    // notification bell, the calendar mail badge) clip off the far edge. Both
    // derive the slot count through the same helper.
    val calendarEventCount =
        if (config.widgets.showCalendar) calendarUpcomingCount(calendarState, calendarTick) else 0
    // The weather chip draws while a forecast is loaded; the expanded outlook
    // page exists under the same condition the overlay uses, so both sizers
    // claim the identical footprint.
    val weatherState by Weather.state.collectAsState()
    val weatherFetching by Weather.fetching.collectAsState()
    val weatherData = (weatherState as? WeatherState.Ready)?.data
    val weatherReady = config.widgets.showWeather &&
        (weatherData != null || weatherFetching || weatherState is WeatherState.Loading)
    val indicatorSlots = quickViewIndicatorSlots(
        config, musicLive, battery, deviceBattery, notificationCount, calendarEventCount,
        weatherVisible = weatherData != null
    )
    val metrics = remember(config, favorites.size, musicLive, indicatorSlots, calendarConnected, weatherReady) {
        computeNotchMetrics(config, favorites.size, musicLive, indicatorSlots, calendarConnected, weatherReady)
    }
    // The native window keeps the expanded footprint at all times (see class
    // doc): opening/collapsing never resizes the transparent window, which is
    // what would flash on macOS. The visible island animates inside it instead.
    val targetSize = metrics.expanded

    val overlayState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.TopCenter),
        size = targetSize
    )

    Window(
        onCloseRequest = { },
        state = overlayState,
        title = stringResource(Res.string.app_name),
        icon = BitmapPainter(appIcon),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = config.alwaysOnTop
    ) {
        val awtWindow = window

        // Keep the fixed window's transparent margin click-through: only the
        // drawn island is a touch target (SetWindowRgn on Windows, cursor-gated
        // ignoresMouseEvents on macOS). Fails soft — no passthrough if the
        // native handle can't be resolved.
        val passthrough = remember(awtWindow) { WindowPassthrough.install(awtWindow) }
        DisposableEffect(awtWindow) {
            onDispose { passthrough?.close() }
        }

        // macOS above-menu-bar placement: AWT can't reach the true top of the
        // screen (it clamps to the visible area), so a direct Cocoa policy parks
        // the island there — raised above the menu bar, joining every Space and
        // surviving full-screen apps while "always on top" is on. The policy is
        // re-asserted on a slow reconcile tick because Compose placement and
        // AWT's z-order bookkeeping keep re-parking the window below the menu
        // bar; `update` just retargets the flags it re-adheres to. Off-mac this
        // installs nothing.
        val macPolicy = remember(awtWindow) { MacOverlayWindowPolicy.install(awtWindow) }
        LaunchedEffect(config.alwaysOnTop, metrics.edgeGapDp) {
            macPolicy?.update(config.alwaysOnTop, metrics.edgeGapDp)
        }
        DisposableEffect(awtWindow) {
            onDispose { macPolicy?.close() }
        }

        // Shared widget-pager state; on Windows the wheel listener below drives
        // it straight from AWT because the unfocused overlay window never sees
        // scroll through Compose's pointer pipeline (which macOS' trackpad does).
        val density = LocalDensity.current
        val pager = remember { OverlayPager(with(density) { 30.dp.toPx() }) }

        // Windows: one wheel notch on the whole overlay window maps onto one
        // pager step through AWT (the layer that actually receives
        // WM_MOUSEWHEEL) — independent of what is under the cursor or window
        // focus. macOS keeps its Compose-path handling inside the overlay.
        if (HostPlatform.isWindows) {
            DisposableEffect(awtWindow, pager) {
                val listener = MouseWheelListener { event ->
                    pager.consumeWheel(event.preciseWheelRotation.toFloat() * pager.stepPx)
                }
                awtWindow.addMouseWheelListener(listener)
                onDispose { awtWindow.removeMouseWheelListener(listener) }
            }
        }

        LaunchedEffect(config.style, targetSize) {
            // Absolute placement is only honoured once the AWT window is mapped:
            // an assignment made earlier is silently dropped and the window keeps
            // its aligned placeholder position (top-centre) instead of hugging
            // the screen edge. Wait for the window to show, give it a frame to
            // finish its initial layout, then pin it to the edge atomically.
            while (!awtWindow.isShowing) delay(10)
            delay(100)
            val graphicsConfiguration = awtWindow.graphicsConfiguration
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
            // Window size is expressed in the same AWT units overlayScreenLocation
            // works in, and Compose maps DpSize onto those units 1:1.
            val location = overlayScreenLocation(
                windowWidthPx = targetSize.width.value.roundToInt(),
                screen = graphicsConfiguration.bounds,
                insets = insets,
                hugMenuBar = HostPlatform.isMac,
                edgeGapPx = metrics.edgeGapDp
            )
            overlayState.size = targetSize
            overlayState.position = WindowPosition.Absolute(location.x.dp, location.y.dp)
            // Compose just re-parked the window at AWT's visible-clamped spot
            // (30pt low on macOS). Ask the Cocoa policy to re-adhere it to the
            // true top right after the placement lands instead of waiting for
            // its next reconcile tick.
            delay(120)
            macPolicy?.update(config.alwaysOnTop, metrics.edgeGapDp)
        }

        QuickNotchTheme(darkTheme = config.themeDark) {
            FloatingNotchOverlay(
                repository = repository,
                media = media,
                isExpanded = isExpanded,
                onExpandedChange = onExpandedChange,
                volumePercent = volumePercent,
                volumeMuted = volumeMuted,
                battery = battery,
                deviceBattery = deviceBattery,
                pager = pager,
                onTouchTargetChange = { passthrough?.update(it) },
                awtWindow = awtWindow
            )
        }
    }
}

/**
 * Loads the brand-mark icon shared by the tray and every window: the indigo
 * rounded square with the white notch pill — the exact logo the site header
 * shows next to "QuickNotch". Rendered from [quicknotch-icon.svg] into a taskbar
 * and system-tray PNG kept under desktopMain resources; falls back to a
 * hand-drawn equivalent if the resource is missing at runtime.
 */
private fun loadQuickNotchIcon(): ImageBitmap {
    return try {
        ClassLoader.getSystemResourceAsStream("quicknotch-tray.png")?.use {
            ImageIO.read(it)?.toComposeImageBitmap()
        } ?: createFallbackIcon()
    } catch (_: Throwable) {
        createFallbackIcon()
    }
}

private fun createFallbackIcon(): ImageBitmap = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
    val graphics = createGraphics()
    graphics.setRenderingHint(
        java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
    )
    graphics.color = java.awt.Color(99, 102, 241)
    graphics.fillRoundRect(0, 0, 32, 32, 10, 10)
    graphics.translate(6, 6)
    graphics.scale(20.0 / 24.0, 20.0 / 24.0)
    graphics.color = java.awt.Color.WHITE
    graphics.stroke = java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
    graphics.drawRoundRect(3, 8, 18, 8, 8, 8)
    graphics.fillOval(6, 11, 3, 3)
    graphics.fillRoundRect(10, 11, 5, 3, 2, 2)
    graphics.fillOval(17, 11, 3, 3)
    graphics.dispose()
}.toComposeImageBitmap()
