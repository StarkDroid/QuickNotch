package com.starkdroid.quicknotch.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.add
import com.starkdroid.quicknotch.generated.resources.add_applications
import com.starkdroid.quicknotch.generated.resources.browse
import com.starkdroid.quicknotch.generated.resources.clear
import com.starkdroid.quicknotch.generated.resources.no_apps_pinned
import com.starkdroid.quicknotch.generated.resources.pick_mac_filter
import com.starkdroid.quicknotch.generated.resources.pick_mac_title
import com.starkdroid.quicknotch.generated.resources.pick_win_filter
import com.starkdroid.quicknotch.generated.resources.remove
import com.starkdroid.quicknotch.generated.resources.no_matches
import com.starkdroid.quicknotch.generated.resources.pinned_badge
import com.starkdroid.quicknotch.generated.resources.pinned_items
import com.starkdroid.quicknotch.generated.resources.reorder_handle
import com.starkdroid.quicknotch.generated.resources.reset
import com.starkdroid.quicknotch.generated.resources.search_hint
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.theme.AccentCyan
import com.starkdroid.quicknotch.theme.AccentGreen
import com.starkdroid.quicknotch.theme.AccentIndigo
import com.starkdroid.quicknotch.theme.AccentRed
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsCard
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// Pinned apps panel (Widgets tab → Pinned apps accordion).

@Composable
internal fun PinnedAppsTab(
    favorites: List<AppItem>,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddFavorite: (AppItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onResetDefaults: () -> Unit,
    fixedHeight: Boolean = false
) {
    val filteredApps = remember(scannedApps, searchQuery) {
        if (searchQuery.isBlank()) scannedApps else scannedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    // Precomputed once per favorites change so each row's "already pinned" check
    // is O(1) during scrolling instead of rescanning the list.
    val pinnedPaths = remember(favorites) {
        favorites.mapTo(HashSet()) { it.executablePath.lowercase() }
    }
    // Swing's file chooser is not a composable surface, so its strings are
    // resolved here and captured as plain values.
    val pickMacTitle = stringResource(Res.string.pick_mac_title)
    val pickMacFilter = stringResource(Res.string.pick_mac_filter)
    val pickWindowsFilter = stringResource(Res.string.pick_win_filter)

    // Drag-to-reorder state. The list renders from a local snapshot so swaps
    // apply synchronously under the finger; a single repository write commits
    // the final position when the drag ends. Rows are equal height, but each
    // measured height is tracked anyway so the swap threshold stays honest.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val ordered = remember {
        mutableStateListOf<AppItem>().apply { addAll(favorites) }
    }
    LaunchedEffect(favorites) {
        // Never clobber an in-flight drag; resync once it has committed.
        if (draggingId == null) {
            ordered.clear()
            ordered.addAll(favorites)
        }
    }
    val reorderGapPx = with(LocalDensity.current) { 6.dp.toPx() }

    // Embedded in the widgets accordion the content gets a fixed height so the
    // inner LazyColumns are never measured against an unbounded constraint.
    Row(
        modifier = (if (fixedHeight) {
            Modifier.fillMaxWidth().height(430.dp)
        } else {
            Modifier.fillMaxSize()
        }).padding(bottom = if (fixedHeight) 0.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Left Column: Current Pinned Apps
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = SettingsCard),
            border = BorderStroke(1.dp, SettingsBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.pinned_items),
                            style = AppTypography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SettingsElevated)
                                .border(1.dp, SettingsBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${favorites.size}",
                                style = AppTypography.labelSmall,
                                color = AccentCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = onResetDefaults,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentRed, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.reset), style = AppTypography.labelSmall, color = AccentRed)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_apps_pinned),
                            style = AppTypography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(ordered, key = { _, item -> item.id }, contentType = { _, _ -> "pinnedRow" }) { index, item ->
                            val isDragging = item.id == draggingId
                            PinnedAppRow(
                                item = item,
                                isDragging = isDragging,
                                onDragStart = {
                                    draggingId = item.id
                                    dragStartIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { deltaY ->
                                    dragOffset += deltaY
                                    val current = ordered.indexOfFirst { it.id == draggingId }
                                    if (current < 0) return@PinnedAppRow
                                    val slot = (rowHeights[draggingId] ?: 0) + reorderGapPx
                                    if (slot <= reorderGapPx) return@PinnedAppRow
                                    // Swap only once the dragged row has travelled
                                    // past half the neighbour it is entering.
                                    if (dragOffset > slot / 2f && current < ordered.lastIndex) {
                                        ordered.add(current + 1, ordered.removeAt(current))
                                        dragOffset -= slot
                                    } else if (dragOffset < -slot / 2f && current > 0) {
                                        ordered.add(current - 1, ordered.removeAt(current))
                                        dragOffset += slot
                                    }
                                },
                                onDragEnd = {
                                    val id = draggingId
                                    if (id != null && dragStartIndex >= 0) {
                                        val end = ordered.indexOfFirst { it.id == id }
                                        if (end >= 0 && end != dragStartIndex) onMove(dragStartIndex, end)
                                    }
                                    draggingId = null
                                    dragStartIndex = -1
                                    dragOffset = 0f
                                },
                                onRemove = { onRemoveFavorite(item.id) },
                                onHeightChanged = { height -> rowHeights[item.id] = height },
                                modifier = Modifier
                                    .then(if (isDragging) Modifier else Modifier.animateItem())
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                            )
                        }
                    }
                }
            }
        }

        // Right Column: Search & Add Apps
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = SettingsCard),
            border = BorderStroke(1.dp, SettingsBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(Res.string.add_applications),
                    style = AppTypography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hand-rolled compact search field: Material's OutlinedTextField
                    // enforces a 56dp minimum height, so pinning it to 44dp clipped
                    // the placeholder. BasicTextField has no such floor.
                    val searchFocus = remember { FocusRequester() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SettingsElevated)
                            .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
                            .clickable { searchFocus.requestFocus() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(AccentIndigo),
                            modifier = Modifier
                                .fillMaxHeight()
                                .focusRequester(searchFocus),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(Res.string.search_hint),
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onSearchQueryChange("") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(Res.string.clear),
                                                tint = TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Button(
                        onClick = {
                            pickCustomApplication(
                                macDialogTitle = pickMacTitle,
                                macFilterDescription = pickMacFilter,
                                windowsFilterDescription = pickWindowsFilter
                            )?.let(onAddFavorite)
                        },
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsElevated),
                        border = BorderStroke(1.dp, SettingsBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.browse), color = TextPrimary, style = AppTypography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentIndigo, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.no_matches), style = AppTypography.bodySmall, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Stable keys let LazyColumn recycle rows without
                        // discarding their (already loaded) icon state on scroll.
                        items(filteredApps, key = { it.executablePath }, contentType = { "availableRow" }) { app ->
                            AvailableAppRow(
                                app = app,
                                isPinned = app.executablePath.lowercase() in pinnedPaths,
                                onAdd = { onAddFavorite(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reads the shared icon cache synchronously; only a cold miss loads in the
 * background.
 */
@Composable
internal fun rememberAppIcon(app: AppItem): ImageBitmap? {
    var iconBitmap by remember(app.id) { mutableStateOf(AppIconLoader.getCachedIcon(app.id)) }
    LaunchedEffect(app.id) {
        if (iconBitmap == null) {
            iconBitmap = AppIconLoader.loadIcon(app)
        }
    }
    return iconBitmap
}

// Shared row building blocks (Pinned + Available rows).

/**
 * The icon slot both list rows share: an elevated rounded box that crossfades
 * from the loading shimmer to the resolved bitmap. `key(id)` keeps LazyColumn
 * slot reuse from animating the previous row's icon into the new one.
 */
@Composable
internal fun AppIconSlot(
    id: String,
    bitmap: ImageBitmap?,
    boxSize: Dp,
    iconSize: Dp,
    corner: Dp,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(corner))
            .background(SettingsElevated),
        contentAlignment = Alignment.Center
    ) {
        key(id) {
            Crossfade(
                targetState = bitmap,
                animationSpec = tween(220),
                label = "appIconFade"
            ) { resolved ->
                if (resolved != null) {
                    Image(
                        bitmap = resolved,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(iconSize)
                    )
                } else {
                    ShimmerBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(corner)
                    )
                }
            }
        }
    }
}

/**
 * The name/path column both list rows share; only the text styles differ.
 */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.AppTitleColumn(
    name: String,
    subtitle: String,
    nameStyle: androidx.compose.ui.text.TextStyle,
    subtitleFontSize: TextUnit
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = name,
            style = nameStyle,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = AppTypography.labelSmall,
            color = TextMuted,
            fontSize = subtitleFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PinnedAppRow(
    item: AppItem,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconBitmap = rememberAppIcon(item)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChanged(it.height) }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDragging) SettingsElevated else SettingsElevated)
            .border(
                1.dp,
                if (isDragging) AccentSolid else SettingsBorder,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Drag handle sits at the leading edge, before the app icon.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(Res.string.reorder_handle),
                    tint = if (isDragging) AccentCyan else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            AppIconSlot(
                id = item.id,
                bitmap = iconBitmap,
                boxSize = 34.dp,
                iconSize = 30.dp,
                corner = 7.dp,
                contentDescription = item.name
            )
            Spacer(modifier = Modifier.width(10.dp))
            AppTitleColumn(
                name = item.name,
                subtitle = File(item.executablePath).name,
                nameStyle = AppTypography.bodyMedium,
                subtitleFontSize = 11.sp
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(Res.string.remove),
                tint = AccentRed.copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
internal fun AvailableAppRow(
    app: AppItem,
    isPinned: Boolean,
    onAdd: () -> Unit
) {
    val iconBitmap = rememberAppIcon(app)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SettingsElevated)
            .border(1.dp, SettingsBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            AppIconSlot(
                id = app.id,
                bitmap = iconBitmap,
                boxSize = 28.dp,
                iconSize = 24.dp,
                corner = 6.dp,
                contentDescription = app.name
            )
            Spacer(modifier = Modifier.width(9.dp))
            AppTitleColumn(
                name = app.name,
                subtitle = File(app.executablePath).name,
                nameStyle = AppTypography.bodySmall,
                subtitleFontSize = 10.sp
            )
        }

        if (isPinned) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentGreen.copy(alpha = 0.12f))
                    .border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(stringResource(Res.string.pinned_badge), style = AppTypography.labelSmall, color = AccentGreen, fontSize = 11.sp)
            }
        } else {
            Button(
                onClick = onAdd,
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SettingsCard),
                border = BorderStroke(1.dp, SettingsBorder),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, stringResource(Res.string.add), tint = AccentSolid, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(stringResource(Res.string.add), color = AccentSolid, style = AppTypography.labelSmall, fontSize = 11.sp)
            }
        }
    }
}

private fun pickCustomApplication(
    macDialogTitle: String,
    macFilterDescription: String,
    windowsFilterDescription: String
): AppItem? {
    val chooser = JFileChooser()
    if (HostPlatform.isMac) {
        chooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        chooser.fileFilter = object : javax.swing.filechooser.FileFilter() {
            override fun accept(f: File): Boolean = f.isDirectory
            override fun getDescription(): String = macFilterDescription
        }
        chooser.dialogTitle = macDialogTitle
    } else {
        chooser.fileFilter = FileNameExtensionFilter(windowsFilterDescription, "exe", "lnk")
    }
    val ret = chooser.showOpenDialog(null)
    if (ret != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile ?: return null
    return AppItem(
        id = "custom_${UUID.randomUUID()}",
        name = file.nameWithoutExtension,
        executablePath = file.absolutePath,
        iconPath = file.absolutePath,
        isCustom = true
    )
}
