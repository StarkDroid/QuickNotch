package com.starkdroid.quicknotch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaControllerFactory
import com.starkdroid.quicknotch.media.SystemVolume
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.theme.QuickNotchTheme
import com.starkdroid.quicknotch.ui.FloatingNotchOverlay
import com.starkdroid.quicknotch.ui.OverlayPager
import com.starkdroid.quicknotch.ui.SettingsScreen
import com.starkdroid.quicknotch.ui.computeNotchMetrics
import com.starkdroid.quicknotch.ui.isMusicLive
import com.starkdroid.quicknotch.ui.overlayScreenLocation
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.event.MouseWheelListener
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

fun main() {
    System.setProperty("apple.awt.application.name", "QuickNotch")
    if (HostPlatform.isMac) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.appearance", "system")
        // Skiko's Metal backend can crash on transparent overlay windows on newer
        // macOS releases; the software pipeline keeps the notch stable.
        if (System.getProperty("skiko.renderApi").isNullOrBlank()) {
            System.setProperty("skiko.renderApi", "SOFTWARE")
        }
    }

    application {
        val repository = remember { FavoritesRepository() }
        val config by repository.config.collectAsState()
        val favorites by repository.favorites.collectAsState()

        // One system-wide media feed for the whole app: the tray, the overlay
        // and the settings window all observe the same session.
        val media = remember { MediaControllerFactory.create() }
        val liveSession by media.session.collectAsState()

        // Master volume drives the collapsed music pill's percentage chip; it
        // is polled separately from the session feed (SMTC/MediaRemote carry
        // no volume) and simply stays hidden whenever a query fails.
        val volumePercent by SystemVolume.percent.collectAsState()
        DisposableEffect(Unit) {
            media.start()
            SystemVolume.start()
            onDispose {
                media.stop()
                SystemVolume.stop()
            }
        }

        val musicLive = isMusicLive(config, liveSession)
        var showSettings by remember { mutableStateOf(false) }
        var isNotchVisible by remember { mutableStateOf(true) }
        var isExpanded by remember { mutableStateOf(false) }
        var isWindowExpanded by remember { mutableStateOf(false) }

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
                Separator()
                Item(stringResource(Res.string.reset_defaults), onClick = { repository.resetToDefaults() })
                Separator()
                Item(stringResource(Res.string.menu_quit), onClick = ::exitApplication)
            }
        )

        if (isNotchVisible) {
            NotchOverlayWindow(
                repository = repository,
                media = media,
                musicLive = musicLive,
                appIcon = appIcon,
                isExpanded = isExpanded,
                isWindowExpanded = isWindowExpanded,
                volumePercent = volumePercent,
                onExpandedChange = { isExpanded = it },
                onWindowExpandedChange = { isWindowExpanded = it }
            )
        }

        if (showSettings) {
            val settingsState = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                size = DpSize(880.dp, 660.dp)
            )
            Window(
                onCloseRequest = { showSettings = false },
                state = settingsState,
                title = "QuickNotch Settings",
                icon = BitmapPainter(appIcon),
                resizable = true
            ) {
                QuickNotchTheme(darkTheme = true) {
                    SettingsScreen(
                        repository = repository,
                        media = media,
                        onClose = { showSettings = false }
                    )
                }
            }
        }
    }
}

/**
 * Hosts the transparent overlay window and pins it to the configured screen edge.
 *
 * Size and position are both written to [androidx.compose.ui.window.WindowState]
 * within a single effect so Compose applies them atomically in one pass — this is
 * what keeps the notch perfectly still (no shift, no flash) while it grows and
 * shrinks. The effect re-runs only when the edge or the target footprint changes,
 * never in response to the window's own live dimensions, so there is no feedback
 * loop and no second corrective placement.
 */
@Composable
private fun NotchOverlayWindow(
    repository: FavoritesRepository,
    media: MediaController,
    musicLive: Boolean,
    appIcon: ImageBitmap,
    isExpanded: Boolean,
    isWindowExpanded: Boolean,
    volumePercent: Int?,
    onExpandedChange: (Boolean) -> Unit,
    onWindowExpandedChange: (Boolean) -> Unit
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val metrics = remember(config, favorites.size, musicLive) {
        computeNotchMetrics(config, favorites.size, musicLive)
    }
    val targetSize = metrics.sizeFor(isWindowExpanded)

    val overlayState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.TopCenter),
        size = targetSize
    )

    Window(
        onCloseRequest = { },
        state = overlayState,
        title = "QuickNotch",
        icon = BitmapPainter(appIcon),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = config.alwaysOnTop
    ) {
        val awtWindow = window

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

        LaunchedEffect(config.position, config.style, targetSize) {
            // Absolute placement is only honoured once the AWT window is mapped:
            // an assignment made earlier is silently dropped and the window keeps
            // its aligned placeholder position (top-centre) instead of hugging
            // the chosen edge. Wait for the window to show, give it a frame to
            // finish its initial layout, then pin it to the edge atomically.
            while (!awtWindow.isShowing) delay(10)
            delay(100)
            val graphicsConfiguration = awtWindow.graphicsConfiguration
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
            // Window size is expressed in the same AWT units overlayScreenLocation
            // works in, and Compose maps DpSize onto those units 1:1.
            val location = overlayScreenLocation(
                position = config.position,
                windowWidthPx = targetSize.width.value.roundToInt(),
                windowHeightPx = targetSize.height.value.roundToInt(),
                screen = graphicsConfiguration.bounds,
                insets = insets,
                hugMenuBar = HostPlatform.isMac,
                edgeGapPx = metrics.edgeGapDp
            )
            overlayState.size = targetSize
            overlayState.position = WindowPosition.Absolute(location.x.dp, location.y.dp)
        }

        QuickNotchTheme(darkTheme = config.themeDark) {
            FloatingNotchOverlay(
                repository = repository,
                media = media,
                isExpanded = isExpanded,
                onExpandedChange = onExpandedChange,
                onWindowExpandedChange = onWindowExpandedChange,
                volumePercent = volumePercent,
                pager = pager
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
