package com.starkdroid.quicknotch

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.starkdroid.quicknotch.datasource.Win32ShellIconLoader
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.theme.QuickNotchTheme
import com.starkdroid.quicknotch.ui.FloatingNotchOverlay
import com.starkdroid.quicknotch.ui.SettingsScreen
import java.awt.Toolkit
import java.awt.image.BufferedImage

fun main() = application {
    val repository = remember { FavoritesRepository() }
    val config by repository.config.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var isNotchVisible by remember { mutableStateOf(true) }

    val trayIcon = remember {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g2d = img.createGraphics()
        g2d.color = java.awt.Color(99, 102, 241)
        g2d.fillRoundRect(2, 2, 28, 28, 8, 8)
        g2d.dispose()
        img.toComposeImageBitmap()
    }

    // System Tray
    Tray(
        icon = androidx.compose.ui.graphics.painter.BitmapPainter(trayIcon),
        tooltip = "QuickNotch - Dynamic Island & Dock",
        menu = {
            Item("Open Settings", onClick = { showSettings = true })
            Item(
                if (isNotchVisible) "Hide Dynamic Island" else "Show Dynamic Island",
                onClick = { isNotchVisible = !isNotchVisible }
            )
            Separator()
            Item("Reset Defaults", onClick = { repository.resetToDefaults() })
            Separator()
            Item("Exit QuickNotch", onClick = ::exitApplication)
        }
    )

    // Floating Dynamic Island Overlay Window
    if (isNotchVisible) {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val overlayWidth = 1000.dp
        val overlayHeight = 110.dp

        val windowPosition = when (config.position) {
            NotchPosition.TOP_CENTER -> WindowPosition(
                x = ((screenSize.width - 1000) / 2).dp,
                y = 0.dp
            )
            NotchPosition.BOTTOM_CENTER -> WindowPosition(
                x = ((screenSize.width - 1000) / 2).dp,
                y = (screenSize.height - 120).dp
            )
            NotchPosition.TOP_LEFT -> WindowPosition(x = 24.dp, y = 0.dp)
            NotchPosition.TOP_RIGHT -> WindowPosition(x = (screenSize.width - 1024).dp, y = 0.dp)
            NotchPosition.BOTTOM_LEFT -> WindowPosition(x = 24.dp, y = (screenSize.height - 120).dp)
            NotchPosition.BOTTOM_RIGHT -> WindowPosition(x = (screenSize.width - 1024).dp, y = (screenSize.height - 120).dp)
        }

        val overlayState = rememberWindowState(
            position = windowPosition,
            size = DpSize(overlayWidth, overlayHeight)
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = overlayState,
            title = "QuickNotch",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = config.alwaysOnTop
        ) {
            QuickNotchTheme(darkTheme = config.themeDark) {
                FloatingNotchOverlay(
                    repository = repository,
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }

    // Settings Window
    if (showSettings) {
        val settingsState = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(840.dp, 640.dp)
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
