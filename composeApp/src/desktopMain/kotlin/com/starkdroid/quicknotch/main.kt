package com.starkdroid.quicknotch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.theme.QuickNotchTheme
import com.starkdroid.quicknotch.ui.FloatingNotchOverlay
import com.starkdroid.quicknotch.ui.SettingsScreen
import com.starkdroid.quicknotch.ui.computeNotchMetrics
import com.starkdroid.quicknotch.ui.overlayScreenLocation
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

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

        var showSettings by remember { mutableStateOf(false) }
        var isNotchVisible by remember { mutableStateOf(true) }
        var isExpanded by remember { mutableStateOf(false) }
        var isWindowExpanded by remember { mutableStateOf(false) }

        val trayIcon = remember { createTrayIcon() }
        Tray(
            icon = BitmapPainter(trayIcon),
            tooltip = "QuickNotch",
            menu = {
                Item("Settings", onClick = { showSettings = true })
                Item(
                    if (isNotchVisible) "Hide Notch" else "Show Notch",
                    onClick = { isNotchVisible = !isNotchVisible }
                )
                Separator()
                Item("Reset Defaults", onClick = { repository.resetToDefaults() })
                Separator()
                Item("Quit QuickNotch", onClick = ::exitApplication)
            }
        )

        if (isNotchVisible) {
            NotchOverlayWindow(
                repository = repository,
                isExpanded = isExpanded,
                isWindowExpanded = isWindowExpanded,
                onExpandedChange = { isExpanded = it },
                onWindowExpandedChange = { isWindowExpanded = it },
                onOpenSettings = { showSettings = true }
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
                resizable = true
            ) {
                QuickNotchTheme(darkTheme = true) {
                    SettingsScreen(repository = repository, onClose = { showSettings = false })
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
    isExpanded: Boolean,
    isWindowExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onWindowExpandedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val metrics = remember(config, favorites.size) { computeNotchMetrics(config, favorites.size) }
    val targetSize = metrics.sizeFor(isWindowExpanded)

    val overlayState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.TopCenter),
        size = targetSize
    )

    Window(
        onCloseRequest = { },
        state = overlayState,
        title = "QuickNotch",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = config.alwaysOnTop
    ) {
        val awtWindow = window

        LaunchedEffect(config.position, targetSize) {
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
                hugMenuBar = HostPlatform.isMac
            )
            overlayState.size = targetSize
            overlayState.position = WindowPosition.Absolute(location.x.dp, location.y.dp)
        }

        QuickNotchTheme(darkTheme = config.themeDark) {
            FloatingNotchOverlay(
                repository = repository,
                isExpanded = isExpanded,
                onExpandedChange = onExpandedChange,
                onWindowExpandedChange = onWindowExpandedChange,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

private fun createTrayIcon() = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
    val graphics = createGraphics()
    graphics.color = java.awt.Color(18, 18, 18)
    graphics.fillRoundRect(4, 10, 24, 12, 12, 12)
    graphics.color = java.awt.Color(255, 255, 255, 40)
    graphics.drawRoundRect(4, 10, 24, 12, 12, 12)
    graphics.dispose()
}.toComposeImageBitmap()
