package com.starkdroid.quicknotch

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.Tray
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.theme.QuickNotchTheme
import com.starkdroid.quicknotch.ui.FloatingNotchOverlay
import com.starkdroid.quicknotch.ui.SettingsScreen
import com.starkdroid.quicknotch.ui.overlayScreenLocation
import com.starkdroid.quicknotch.ui.overlayWindowSize
import com.starkdroid.quicknotch.ui.snapToNotchPosition
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

fun main() {
    System.setProperty("apple.awt.application.name", "QuickNotch")
    if (HostPlatform.isMac) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.appearance", "system")
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
        var isDragging by remember { mutableStateOf(false) }

        val trayIcon = remember {
            val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            val g2d = img.createGraphics()
            g2d.color = java.awt.Color(18, 18, 18)
            g2d.fillRoundRect(4, 10, 24, 12, 12, 12)
            g2d.color = java.awt.Color(255, 255, 255, 40)
            g2d.drawRoundRect(4, 10, 24, 12, 12, 12)
            g2d.dispose()
            img.toComposeImageBitmap()
        }

        Tray(
            icon = androidx.compose.ui.graphics.painter.BitmapPainter(trayIcon),
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
            val windowSize = overlayWindowSize(config, favorites.size, isWindowExpanded)
            val overlayState = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.TopCenter),
                size = windowSize
            )

            LaunchedEffect(windowSize) {
                overlayState.size = windowSize
            }

            Window(
                onCloseRequest = ::exitApplication,
                state = overlayState,
                title = "QuickNotch",
                undecorated = true,
                transparent = true,
                resizable = false,
                alwaysOnTop = config.alwaysOnTop
            ) {
                val awtWindow = window

                fun placeOnEdge() {
                    val screen = awtWindow.graphicsConfiguration.bounds
                    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(awtWindow.graphicsConfiguration)
                    val location = overlayScreenLocation(
                        position = config.position,
                        windowWidthPx = awtWindow.width.coerceAtLeast(1),
                        windowHeightPx = awtWindow.height.coerceAtLeast(1),
                        screen = screen,
                        insets = insets,
                        hugMenuBar = HostPlatform.isMac
                    )
                    awtWindow.setLocation(location.x, location.y)
                }

                LaunchedEffect(config.position, windowSize, awtWindow.width, awtWindow.height) {
                    if (!isDragging) placeOnEdge()
                }

                QuickNotchTheme(darkTheme = config.themeDark) {
                    FloatingNotchOverlay(
                        repository = repository,
                        isExpanded = isExpanded,
                        onExpandedChange = { isExpanded = it },
                        onWindowExpandedChange = { isWindowExpanded = it },
                        onDragReposition = { dx, dy ->
                            isDragging = true
                            awtWindow.setLocation(
                                awtWindow.x + dx.roundToInt(),
                                awtWindow.y + dy.roundToInt()
                            )
                        },
                        onDragRepositionEnd = {
                            val screen = awtWindow.graphicsConfiguration.bounds
                            val snapped = snapToNotchPosition(
                                centerX = awtWindow.x + awtWindow.width / 2,
                                centerY = awtWindow.y + awtWindow.height / 2,
                                screen = screen
                            )
                            isDragging = false
                            if (snapped != config.position) {
                                repository.updateConfig { it.copy(position = snapped) }
                            } else {
                                placeOnEdge()
                            }
                        },
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
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
                    SettingsScreen(
                        repository = repository,
                        onClose = { showSettings = false }
                    )
                }
            }
        }
    }
}
