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
    val favorites by repository.favorites.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var isNotchVisible by remember { mutableStateOf(true) }
    var isExpanded by remember { mutableStateOf(false) }

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
        val ge = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment() }
        val gc = remember { ge.defaultScreenDevice.defaultConfiguration }
        val bounds = remember(gc) { gc.bounds }
        val insets = remember(gc) { java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc) }

        val screenWidth = bounds.width
        val screenHeight = bounds.height

        val isVertical = config.position == NotchPosition.LEFT_CENTER || config.position == NotchPosition.RIGHT_CENTER

        val baseHorizontalExpandedWidth = (favorites.size * (config.baseIconSizeDp + 18) + 140).coerceIn(360, 980)
        val baseVerticalExpandedHeight = (favorites.size * (config.baseIconSizeDp + 18) + 140).coerceIn(320, 800)

        // Strict overlay window dimensions:
        // Sized so that the edge touching the screen bezel is flush with zero spacing,
        // while allowing a modest shadow margin only on the open edges facing into the display.
        val currentWindowWidth = if (isVertical) {
            if (isExpanded) (config.expandedNotchHeightDp + 8).dp else (config.collapsedPillHeightDp + 6).dp
        } else {
            if (isExpanded) (baseHorizontalExpandedWidth + 12).dp else (config.collapsedPillWidthDp + 10).dp
        }

        val currentWindowHeight = if (isVertical) {
            if (isExpanded) (baseVerticalExpandedHeight + 12).dp else (config.collapsedPillWidthDp + 10).dp
        } else {
            if (isExpanded) (config.expandedNotchHeightDp + 8).dp else (config.collapsedPillHeightDp + 4).dp
        }

        val availableWidth = screenWidth - insets.left - insets.right
        val availableHeight = screenHeight - insets.top - insets.bottom

        val windowPosition = remember(config.position, isExpanded, bounds, insets, currentWindowWidth, currentWindowHeight) {
            val winW = currentWindowWidth.value
            val winH = currentWindowHeight.value

            when (config.position) {
                NotchPosition.TOP_CENTER -> WindowPosition(
                    x = (bounds.x + insets.left + (availableWidth - winW) / 2).dp,
                    y = (bounds.y + insets.top).dp
                )
                NotchPosition.BOTTOM_CENTER -> WindowPosition(
                    x = (bounds.x + insets.left + (availableWidth - winW) / 2).dp,
                    y = (bounds.y + screenHeight - insets.bottom - winH).coerceAtLeast(0f).dp
                )
                NotchPosition.TOP_LEFT -> WindowPosition(
                    x = (bounds.x + insets.left + 16).dp,
                    y = (bounds.y + insets.top).dp
                )
                NotchPosition.TOP_RIGHT -> WindowPosition(
                    x = (bounds.x + screenWidth - insets.right - winW - 16).dp,
                    y = (bounds.y + insets.top).dp
                )
                NotchPosition.BOTTOM_LEFT -> WindowPosition(
                    x = (bounds.x + insets.left + 16).dp,
                    y = (bounds.y + screenHeight - insets.bottom - winH).coerceAtLeast(0f).dp
                )
                NotchPosition.BOTTOM_RIGHT -> WindowPosition(
                    x = (bounds.x + screenWidth - insets.right - winW - 16).dp,
                    y = (bounds.y + screenHeight - insets.bottom - winH).coerceAtLeast(0f).dp
                )
                NotchPosition.LEFT_CENTER -> WindowPosition(
                    x = (bounds.x + insets.left).dp,
                    y = (bounds.y + insets.top + (availableHeight - winH) / 2).dp
                )
                NotchPosition.RIGHT_CENTER -> WindowPosition(
                    x = (bounds.x + screenWidth - insets.right - winW).dp,
                    y = (bounds.y + insets.top + (availableHeight - winH) / 2).dp
                )
            }
        }

        val overlayState = rememberWindowState(
            position = windowPosition,
            size = DpSize(currentWindowWidth, currentWindowHeight)
        )

        LaunchedEffect(windowPosition) {
            overlayState.position = windowPosition
        }

        LaunchedEffect(currentWindowWidth, currentWindowHeight) {
            overlayState.size = DpSize(currentWindowWidth, currentWindowHeight)
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
            QuickNotchTheme(darkTheme = config.themeDark) {
                FloatingNotchOverlay(
                    repository = repository,
                    isExpanded = isExpanded,
                    onExpandedChange = { isExpanded = it },
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }

    // Settings Window
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
