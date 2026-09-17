package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.datasource.Win32ShellIconLoader
import com.starkdroid.quicknotch.datasource.WindowsStartupManager
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun SettingsScreen(
    repository: FavoritesRepository,
    onClose: () -> Unit
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dock & Sizing", "Pinned Apps", "Behavior", "About")

    var scannedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isScanning = true
        scannedApps = InstalledAppsScanner.scanAllInstalledApps()
        isScanning = false
    }

    Scaffold(
        containerColor = NotchDeepBlack,
        topBar = {
            SettingsTopBar(onClose = onClose)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = NotchSurface,
                contentColor = AccentIndigoLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, NotchBorder, RoundedCornerShape(12.dp))
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = AppTypography.labelLarge,
                                color = if (selectedTab == index) Color.White else TextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> DockSizingTab(config = config, onUpdate = { repository.updateConfig(it) })
                1 -> PinnedAppsTab(
                    favorites = favorites,
                    scannedApps = scannedApps,
                    searchQuery = searchQuery,
                    isScanning = isScanning,
                    onSearchQueryChange = { searchQuery = it },
                    onAddFavorite = { repository.addFavorite(it) },
                    onRemoveFavorite = { repository.removeFavorite(it) },
                    onMoveUp = { i -> if (i > 0) repository.reorderFavorites(i, i - 1) },
                    onMoveDown = { i -> if (i < favorites.size - 1) repository.reorderFavorites(i, i + 1) },
                    onResetDefaults = { repository.resetToDefaults() }
                )
                2 -> BehaviorTab(
                    config = config,
                    onUpdate = { repository.updateConfig(it) }
                )
                3 -> AboutTab()
            }
        }
    }
}

@Composable
private fun SettingsTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentIndigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = "QuickNotch",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "QuickNotch Settings",
                    style = AppTypography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Dynamic Island & Dock Customizer",
                    style = AppTypography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DockSizingTab(
    config: NotchConfig,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NotchSurface),
                border = BorderStroke(1.dp, NotchBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Screen Edge Position", style = AppTypography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    NotchPosition.values().forEach { pos ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdate { it.copy(position = pos) } }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.position == pos,
                                onClick = { onUpdate { it.copy(position = pos) } },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentIndigo)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(pos.displayName, style = AppTypography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NotchSurface),
                border = BorderStroke(1.dp, NotchBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Mac Dock Fisheye Magnification", style = AppTypography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Magnification Peak Scale: ${String.format("%.2f", config.dockScaleFactor)}x", style = AppTypography.bodyMedium, color = TextSecondary)
                    Slider(
                        value = config.dockScaleFactor,
                        onValueChange = { scale -> onUpdate { it.copy(dockScaleFactor = scale) } },
                        valueRange = 1.0f..2.4f,
                        colors = SliderDefaults.colors(thumbColor = AccentIndigo, activeTrackColor = AccentIndigo)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Magnification Wave Radius: ${config.dockMagnificationRadius.toInt()} px", style = AppTypography.bodyMedium, color = TextSecondary)
                    Slider(
                        value = config.dockMagnificationRadius,
                        onValueChange = { r -> onUpdate { it.copy(dockMagnificationRadius = r) } },
                        valueRange = 70f..220f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Base Icon Size: ${config.baseIconSizeDp} dp", style = AppTypography.bodyMedium, color = TextSecondary)
                    Slider(
                        value = config.baseIconSizeDp.toFloat(),
                        onValueChange = { s -> onUpdate { it.copy(baseIconSizeDp = s.toInt()) } },
                        valueRange = 26f..56f,
                        colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedAppsTab(
    favorites: List<AppItem>,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddFavorite: (AppItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onResetDefaults: () -> Unit
) {
    val filteredApps = remember(scannedApps, searchQuery) {
        if (searchQuery.isBlank()) scannedApps else scannedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Current Pinned Apps
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = NotchSurface),
            border = BorderStroke(1.dp, NotchBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pinned Items (${favorites.size})", style = AppTypography.titleMedium, color = TextPrimary)
                    TextButton(onClick = onResetDefaults) {
                        Text("Reset Defaults", style = AppTypography.labelMedium, color = AccentRed)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(favorites) { index, item ->
                        PinnedAppRow(
                            item = item,
                            canMoveUp = index > 0,
                            canMoveDown = index < favorites.size - 1,
                            onMoveUp = { onMoveUp(index) },
                            onMoveDown = { onMoveDown(index) },
                            onRemove = { onRemoveFavorite(item.id) }
                        )
                    }
                }
            }
        }

        // Right Column: Search & Add Apps
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = NotchSurface),
            border = BorderStroke(1.dp, NotchBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Applications", style = AppTypography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search installed apps...", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentIndigo,
                            unfocusedBorderColor = NotchBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val chooser = JFileChooser()
                            chooser.fileFilter = FileNameExtensionFilter("Executables & Shortcuts (*.exe, *.lnk)", "exe", "lnk")
                            val ret = chooser.showOpenDialog(null)
                            if (ret == JFileChooser.APPROVE_OPTION) {
                                val file = chooser.selectedFile
                                onAddFavorite(
                                    AppItem(
                                        id = "custom_${UUID.randomUUID()}",
                                        name = file.nameWithoutExtension,
                                        executablePath = file.absolutePath,
                                        iconPath = file.absolutePath,
                                        isCustom = true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NotchCard),
                        border = BorderStroke(1.dp, NotchBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Browse", color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentIndigo)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps.size) { i ->
                            val app = filteredApps[i]
                            val isAlreadyPinned = favorites.any { it.executablePath.equals(app.executablePath, ignoreCase = true) }

                            AvailableAppRow(
                                app = app,
                                isPinned = isAlreadyPinned,
                                onAdd = { onAddFavorite(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedAppRow(
    item: AppItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item) {
        iconBitmap = Win32ShellIconLoader.loadIcon(item)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NotchCard)
            .border(1.dp, NotchBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap!!, contentDescription = item.name, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentIndigo))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.name,
                style = AppTypography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowUpward, "Move Up", tint = if (canMoveUp) TextPrimary else TextMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowDownward, "Move Down", tint = if (canMoveDown) TextPrimary else TextMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Remove", tint = AccentRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AvailableAppRow(
    app: AppItem,
    isPinned: Boolean,
    onAdd: () -> Unit
) {
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app) {
        iconBitmap = Win32ShellIconLoader.loadIcon(app)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NotchCard)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap!!, contentDescription = app.name, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentIndigo))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = app.name,
                style = AppTypography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isPinned) {
            Text("Pinned", style = AppTypography.labelSmall, color = AccentGreen)
        } else {
            IconButton(onClick = onAdd, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, "Add", tint = AccentCyan, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BehaviorTab(
    config: NotchConfig,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NotchSurface),
        border = BorderStroke(1.dp, NotchBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("System & Startup Behaviors", style = AppTypography.titleMedium, color = TextPrimary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Start on Windows Boot", style = AppTypography.bodyMedium, color = TextPrimary)
                    Text("Launch QuickNotch automatically on user sign-in", style = AppTypography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = config.autoStartOnBoot,
                    onCheckedChange = { checked ->
                        WindowsStartupManager.setAutoStartEnabled(checked)
                        onUpdate { it.copy(autoStartOnBoot = checked) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentIndigo)
                )
            }

            HorizontalDivider(color = NotchBorder.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Always on Top", style = AppTypography.bodyMedium, color = TextPrimary)
                    Text("Keep the Dynamic Island visible above other windows", style = AppTypography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = config.alwaysOnTop,
                    onCheckedChange = { checked -> onUpdate { it.copy(alwaysOnTop = checked) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentIndigo)
                )
            }

            HorizontalDivider(color = NotchBorder.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show App Tooltips", style = AppTypography.bodyMedium, color = TextPrimary)
                    Text("Display frosted glass tooltip badge above active hovered icon", style = AppTypography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = config.showTooltips,
                    onCheckedChange = { checked -> onUpdate { it.copy(showTooltips = checked) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentIndigo)
                )
            }

            HorizontalDivider(color = NotchBorder.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Collapse on Launch", style = AppTypography.bodyMedium, color = TextPrimary)
                    Text("Automatically collapse Dynamic Island after launching an app", style = AppTypography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = config.hideOnAppLaunch,
                    onCheckedChange = { checked -> onUpdate { it.copy(hideOnAppLaunch = checked) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentIndigo)
                )
            }
        }
    }
}

@Composable
private fun AboutTab() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NotchSurface),
        border = BorderStroke(1.dp, NotchBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentIndigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = "QuickNotch",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text("QuickNotch Desktop", style = AppTypography.titleLarge, color = TextPrimary)
            Text("Version 1.0.0", style = AppTypography.labelLarge, color = AccentCyan)

            Text(
                text = "Dynamic Island & Dock floating app launcher for Windows 10 & 11 built with Kotlin Multiplatform and Jetpack Compose Desktop.",
                style = AppTypography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            Text("© 2025 StarkDroid. All rights reserved.", style = AppTypography.labelSmall, color = TextMuted)
        }
    }
}
