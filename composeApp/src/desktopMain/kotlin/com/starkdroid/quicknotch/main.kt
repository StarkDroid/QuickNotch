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
import com.starkdroid.quicknotch.datasource.AppUpdates
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.StartupManager
import com.starkdroid.quicknotch.datasource.UpdateState
import com.starkdroid.quicknotch.datasource.MacDockPresence
import com.starkdroid.quicknotch.datasource.MacOverlayWindowPolicy
import com.starkdroid.quicknotch.datasource.ProjectLinks
import com.starkdroid.quicknotch.datasource.WindowPassthrough
import com.starkdroid.quicknotch.datasource.WindowsOverlayWindowPolicy
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.app_name
import com.starkdroid.quicknotch.generated.resources.menu_always_on_top
import com.starkdroid.quicknotch.generated.resources.menu_always_on_top_checked
import com.starkdroid.quicknotch.generated.resources.menu_hide_notch
import com.starkdroid.quicknotch.generated.resources.menu_quit
import com.starkdroid.quicknotch.generated.resources.menu_settings
import com.starkdroid.quicknotch.generated.resources.menu_support
import com.starkdroid.quicknotch.generated.resources.menu_show_notch
import com.starkdroid.quicknotch.generated.resources.menu_update_available
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
import com.starkdroid.quicknotch.system.FpsProbe
import com.starkdroid.quicknotch.system.GameDetector
import com.starkdroid.quicknotch.system.SystemMetrics
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
 * Ensures one notch: a second process would stack an identical overlay that reads as a single
 * frozen pill. Fails open, since a lock hiccup must never prevent launch.
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
        // Skiko backend: an explicit -Dskiko.renderApi / SKIKO_RENDER_API wins, then the
        // `software-render` marker file (a crash can hide the UI that would remove it), then METAL.
        val marker = AppPaths.configDir.resolve("software-render")
        val backend = System.getProperty("skiko.renderApi")?.takeIf { it.isNotBlank() }
            ?: System.getenv("SKIKO_RENDER_API")?.takeIf { it.isNotBlank() }
            ?: (if (marker.exists()) "SOFTWARE" else "METAL")
        System.setProperty("skiko.renderApi", backend)
        println("QuickNotch: skiko render backend = $backend${if (marker.exists()) " (marker file)" else ""}")
        // Clean up bridges leaked by earlier runs: JVM death skips media.stop().
        NativeResources.sweepOrphanedBridges()
    }
    // A manual launch opens Settings so the app is obvious; a login-item auto-start passes
    // `--login-item` (written by StartupManager) and stays quietly behind the tray.
    val loginItemLaunch = args.contains(StartupManager.LAUNCHED_AT_LOGIN_ARG)

    application {
        val repository = remember { FavoritesRepository() }
        val config by repository.config.collectAsState()
        val favorites by repository.favorites.collectAsState()

        // One gaming signal for the whole app; `gamingActive` (not the raw session) is the gate,
        // so a user who switches the HUD off pays nothing.
        val gameSession by GameDetector.game.collectAsState()
        // Windows-only: a macOS session must never light up a HUD the user cannot see or turn off.
        val gamingActive = HostPlatform.isWindows &&
            gameSession != null &&
            config.gameHudEnabled

        // One system-wide media feed for the whole app: the tray, the overlay
        // and the settings window all observe the same session.
        val media = remember { MediaControllerFactory.create() }
        DisposableEffect(Unit) {
            media.start()
            SystemVolume.start()
            AudioBattery.start()
            SystemBattery.start()
            SystemMetrics.start()
            // Gaming is a Windows feature end to end; the detector is one long-lived PowerShell
            // watcher (see GameDetector), and a Mac would only probe for a HUD gated off below.
            if (HostPlatform.isWindows) {
                GameDetector.start()
                GameDetector.logAvailability()
            }
            NotificationCount.start()
            // Calendar is opt-in and file-backed: with no token on disk this
            // returns immediately, so an unconnected user pays nothing.
            GoogleCalendar.start()
            // Weather caches the day to disk, so a cold start paints the last forecast on the
            // first frame; a manual place from settings seeds the position instead.
            Weather.start(repository.config.value.widgets.weatherPlace)
            // Update checks and the remote changelog are background work; the app stays usable offline.
            AppUpdates.start()
            onDispose {
                media.stop()
                SystemVolume.stop()
                AudioBattery.stop()
                SystemBattery.stop()
                SystemMetrics.stop()
                GameDetector.stop()
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

        // The installer script waits for this PID to vanish before it replaces the app.
        val quittingForUpdate by AppUpdates.quitting.collectAsState()
        LaunchedEffect(quittingForUpdate) {
            if (quittingForUpdate) exitApplication()
        }

        // Settings, or onboarding, forces activation: an accessory agent cannot bring a regular
        // window forward on its own, and closing returns to the quiet agent state.
        LaunchedEffect(showSettings, onboardingDone) {
            MacDockPresence.setSettingsVisible(showSettings || !onboardingDone)
        }

        // A manual place replaces the IP position (null re-locates automatically); the controller
        // dedupes re-emits, so unrelated config writes never cost a refetch.
        LaunchedEffect(config.widgets.weatherPlace) {
            Weather.setManualPlace(config.widgets.weatherPlace)
        }

        // The system monitor only polls while its widget is on; the gaming HUD is the exception,
        // and a game switches the poller on even when the page stays off the wheel.
        LaunchedEffect(config.widgets.showSystem, gamingActive) {
            SystemMetrics.setEnabled(config.widgets.showSystem || gamingActive)
        }

        // Detection runs only while the HUD can draw: its switch is on and at least one metric is selected.
        LaunchedEffect(config.gameHudEnabled) {
            GameDetector.setEnabled(config.gameHudEnabled)
        }

        // A PresentMon capture takes ~2 s, so it gets its own coroutine and never delays a system
        // sample; cancelling the effect stops it when the game ends.
        LaunchedEffect(gameSession?.pid, gamingActive) {
            if (gamingActive) FpsProbe.follow(gameSession)
        }

        // Started from DisposableEffect(Unit), never the composition body: the body re-runs on
        // every media/volume/battery tick, and a raw call re-armed the activation and stole focus.
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
                // Collected inside the menu so a download's progress ticks only
                // touch the tray menu, never the overlay window tree.
                val update = AppUpdates.state.collectAsState().value
                val available = update as? UpdateState.Available
                if (available != null) {
                    Item(
                        stringResource(Res.string.menu_update_available, available.version),
                        onClick = { showSettings = true }
                    )
                }
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
                Item(
                    stringResource(Res.string.menu_support),
                    onClick = { ProjectLinks.open(ProjectLinks.KOFI) }
                )
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

        // First-run users open straight into onboarding (even at login launch); finishing hands
        // the same window to settings and the notch appears for the first time.
        if (showSettings || !onboardingDone) {
            val settingsState = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                size = DpSize(880.dp, 660.dp)
            )
            Window(
                onCloseRequest = {
                    // Closing mid-onboarding finishes the flow: the notch must exist, and config
                    // the user already touched stays.
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
                            // Re-read the notification store on focus, so granting Full Disk Access
                            // updates the helper row now instead of at the next 3s tick.
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
                                media = media
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
 * Hosts the transparent overlay on one fixed footprint: resizing it on macOS flashes white and
 * skips frames, so the island grows inside a stable window. Size and position land in one effect.
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
    // Calendar width is reserved only while the widget is on and connected, the same rule the
    // pager follows, so a toggled-off calendar neither pages nor widens the window.
    val calendarConnected =
        config.widgets.showCalendar && calendarState is CalendarState.Connected
    // Window and content must derive the indicator slot count through quickViewIndicatorSlots,
    // or the right-edge icons clip.
    val calendarEventCount =
        if (config.widgets.showCalendar) calendarUpcomingCount(calendarState, calendarTick) else 0
    // The chip and the outlook page share one condition, so both sizers claim the same footprint.
    val weatherState by Weather.state.collectAsState()
    val weatherFetching by Weather.fetching.collectAsState()
    val weatherData = (weatherState as? WeatherState.Ready)?.data
    val weatherReady = config.widgets.showWeather &&
        (weatherData != null || weatherFetching || weatherState is WeatherState.Loading)
    // Same gating rule as the overlay: the System page (and its width) only
    // exists once there is a reading behind it.
    val systemState by SystemMetrics.state.collectAsState()
    val systemReady = config.widgets.showSystem && systemState != null
    // The window must already be wide enough the instant a game starts; resizing then would flash on macOS.
    val game by GameDetector.game.collectAsState()
    val gameHudActive = HostPlatform.isWindows && game != null && config.gameHudEnabled
    val indicatorSlots = quickViewIndicatorSlots(
        config, musicLive, battery, deviceBattery, notificationCount, calendarEventCount,
        weatherVisible = weatherData != null
    )
    val metrics = remember(
        config,
        favorites.size,
        musicLive,
        indicatorSlots,
        calendarConnected,
        weatherReady,
        systemReady,
        gameHudActive
    ) {
        computeNotchMetrics(
            config,
            favorites.size,
            musicLive,
            indicatorSlots,
            calendarConnected,
            weatherReady,
            systemReady,
            gameHudActive
        )
    }
    // `window` (not `expanded`) reserves the widest thing the island can draw, so the gaming HUD
    // needs no resize either.
    val targetSize = metrics.window

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

        // Keep the fixed window's transparent margin click-through: only the drawn island is a
        // touch target. Fails soft, with no passthrough when the native handle cannot be resolved.
        val passthrough = remember(awtWindow) { WindowPassthrough.install(awtWindow) }
        DisposableEffect(awtWindow) {
            onDispose { passthrough?.close() }
        }

        // AWT clamps to the visible area, so a Cocoa policy parks the island above the menu bar
        // and re-asserts it on a reconcile tick, because Compose keeps re-parking the window.
        val macPolicy = remember(awtWindow) { MacOverlayWindowPolicy.install(awtWindow) }
        LaunchedEffect(config.alwaysOnTop, metrics.edgeGapDp) {
            macPolicy?.update(config.alwaysOnTop, metrics.edgeGapDp)
        }
        DisposableEffect(awtWindow) {
            onDispose { macPolicy?.close() }
        }

        // A fullscreen game re-raises its own window into the topmost band, so re-assert
        // HWND_TOPMOST on a tick. Exclusive fullscreen still cannot be overlaid; borderless can.
        val windowsPolicy = remember(awtWindow) { WindowsOverlayWindowPolicy.install(awtWindow) }
        LaunchedEffect(config.alwaysOnTop, game != null) {
            windowsPolicy?.update(config.alwaysOnTop, game != null)
        }
        DisposableEffect(awtWindow) {
            onDispose { windowsPolicy?.close() }
        }

        // On Windows the unfocused overlay never sees scroll through Compose, so AWT drives the
        // pager directly; macOS keeps its Compose path.
        val density = LocalDensity.current
        val pager = remember { OverlayPager(with(density) { 30.dp.toPx() }) }

        // One wheel notch maps onto one pager step straight from AWT, the layer that receives WM_MOUSEWHEEL.
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
            // Absolute placement is silently dropped before the window is mapped: wait for
            // isShowing, let layout settle, then pin it to the edge.
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
            // Compose just re-parked the window at AWT's visible-clamped spot (30pt low on macOS),
            // so re-adhere to the true top instead of waiting for the next reconcile tick.
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

/** The brand mark shared by the tray and every window, with a hand-drawn fallback if the resource is missing. */
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
