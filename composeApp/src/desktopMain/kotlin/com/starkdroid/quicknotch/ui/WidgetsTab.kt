package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.theme.*
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetsTab(
    config: NotchConfig,
    favorites: List<AppItem>,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    liveSession: MediaSession?,
    media: MediaController,
    onSearchQueryChange: (String) -> Unit,
    onAddFavorite: (AppItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.widgets_header),
                    style = AppTypography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = stringResource(Res.string.widgets_header_desc),
                    style = AppTypography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "pinnedApps") {
                WidgetAccordion(
                    icon = Icons.Default.Apps,
                    title = stringResource(Res.string.widget_apps_title),
                    subtitle = stringResource(Res.string.widget_apps_desc),
                    enabled = config.widgets.pinnedAppsEnabled,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(pinnedAppsEnabled = enabled)) }
                    }
                ) {
                    PinnedAppsTab(
                        favorites = favorites,
                        scannedApps = scannedApps,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        onSearchQueryChange = onSearchQueryChange,
                        onAddFavorite = onAddFavorite,
                        onRemoveFavorite = onRemoveFavorite,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onResetDefaults = onResetDefaults,
                        fixedHeight = true
                    )
                }
            }
            item(key = "nowPlaying") {
                WidgetAccordion(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(Res.string.widget_music_title),
                    subtitle = stringResource(Res.string.widget_music_desc),
                    enabled = config.widgets.nowPlayingEnabled,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(nowPlayingEnabled = enabled)) }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NowPlayingStatus(config = config, liveSession = liveSession)

                        Text(
                            text = stringResource(Res.string.widget_scroll_hint),
                            style = AppTypography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        if (!isStyleEligibleForLiveActivity(config)) {
                            Text(
                                text = stringResource(Res.string.widget_style_warning),
                                style = AppTypography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetAccordion(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FlatSurface),
        border = BorderStroke(1.dp, FlatBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(FlatSurfaceElevated)
                        .border(1.dp, FlatBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentIndigoLight,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                MinimalistRectSwitch(checked = enabled, onCheckedChange = onToggleEnabled)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(tween(160)),
                exit = shrinkVertically() + fadeOut(tween(120))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

/**
 * The widget's live status pill: a green dot when a media session is actively
 * feeding the notch, otherwise the rendered state the user can compare against.
 */
@Composable
internal fun NowPlayingStatus(
    config: NotchConfig,
    liveSession: MediaSession?
) {
    val live = isMusicLive(config, liveSession)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (live) AccentGreen else TextMuted.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (live && liveSession != null) {
                stringResource(Res.string.widget_now_playing, liveSession.title, liveSession.artist.ifBlank { "—" })
            } else {
                stringResource(Res.string.widget_no_session)
            },
            style = AppTypography.bodySmall,
            color = if (live) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PinnedAppsTab(
    favorites: List<AppItem>,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddFavorite: (AppItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
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
            colors = CardDefaults.cardColors(containerColor = FlatSurface),
            border = BorderStroke(1.dp, FlatBorder),
            shape = RoundedCornerShape(12.dp)
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
                                .clip(RoundedCornerShape(4.dp))
                                .background(FlatSurfaceElevated)
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
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(favorites, key = { _, item -> item.id }, contentType = { _, _ -> "pinnedRow" }) { index, item ->
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
        }

        // Right Column: Search & Add Apps
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = FlatSurface),
            border = BorderStroke(1.dp, FlatBorder),
            shape = RoundedCornerShape(12.dp)
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(FlatCard)
                            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = FlatSurfaceElevated),
                        border = BorderStroke(1.dp, FlatBorder),
                        shape = RoundedCornerShape(8.dp),
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
 * Resolves an app icon with no visible pop-in while scrolling: the shared
 * in-memory cache is read synchronously on first composition, and only a cold
 * miss falls back to a background load. Combined with stable LazyColumn keys,
 * rows keep their icons when recycled, so the list stays smooth.
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

@Composable
internal fun PinnedAppRow(
    item: AppItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val iconBitmap = rememberAppIcon(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(FlatSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                key(item.id) {
                    Crossfade(
                        targetState = iconBitmap,
                        animationSpec = tween(220),
                        label = "pinnedIconFade"
                    ) { bitmap ->
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = item.name,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(7.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = AppTypography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = File(item.executablePath).name,
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    stringResource(Res.string.move_up),
                    tint = if (canMoveUp) TextSecondary else TextMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    stringResource(Res.string.move_down),
                    tint = if (canMoveDown) TextSecondary else TextMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(Res.string.remove),
                    tint = AccentRed.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp)
                )
            }
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
            .clip(RoundedCornerShape(8.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FlatSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                // key() on the app id: LazyColumn reuses slots while scrolling, and
                // without this the Crossfade would animate from the *previous*
                // row's icon into the new one — visible as scroll jitter.
                key(app.id) {
                    Crossfade(
                        targetState = iconBitmap,
                        animationSpec = tween(220),
                        label = "availableIconFade"
                    ) { bitmap ->
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = app.name,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = AppTypography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = File(app.executablePath).name,
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                modifier = Modifier.height(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlatSurfaceElevated),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, stringResource(Res.string.add), tint = AccentCyan, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(stringResource(Res.string.add), color = AccentCyan, style = AppTypography.labelSmall, fontSize = 11.sp)
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