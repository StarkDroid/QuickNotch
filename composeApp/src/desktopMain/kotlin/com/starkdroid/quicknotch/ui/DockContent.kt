package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.music_audio_device
import com.starkdroid.quicknotch.generated.resources.music_battery
import com.starkdroid.quicknotch.generated.resources.music_mute
import com.starkdroid.quicknotch.generated.resources.music_unmute
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.media.PlaybackState
import com.starkdroid.quicknotch.media.SystemVolume
import com.starkdroid.quicknotch.model.AppItem

import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.system.SystemSnapshot
import com.starkdroid.quicknotch.weather.WeatherState
import com.starkdroid.quicknotch.theme.TextPrimary
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs


internal enum class WidgetPage { Apps, Music, Calendar, Weather, System }

/**
 * Every active page stays composed so the pager never measures the music page mid-animation;
 * inactive pages are offset with alpha+slide, and only the page under the pointer gets input.
 */
@Composable
internal fun WidgetPages(
    pages: List<WidgetPage>,
    pagePosition: State<Float>,
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    session: MediaSession?,
    media: MediaController,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    volumeMuted: Boolean?,
    density: Density,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier,
    calendarState: CalendarState?,
    calendarRefreshing: Boolean,
    weatherState: WeatherState?,
    weatherFetching: Boolean,
    fahrenheit: Boolean,
    /** Latest CPU/GPU/memory/storage/network reading for the System page. */
    system: SystemSnapshot?,
    onRefreshWeather: () -> Unit,
    /** A click anywhere on the System page hands off to the OS task inspector. */
    onOpenSystemMonitor: () -> Unit,
    onLaunch: (AppItem) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        pages.forEachIndexed { index, page ->
            val isActive by remember(index) {
                derivedStateOf { abs(index - pagePosition.value) < 0.5f }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val relative = index - pagePosition.value
                        alpha = (1f - abs(relative)).coerceIn(0f, 1f)
                        translationY = 0.35f * relative * size.height
                    }
            ) {
                when (page) {
                    WidgetPage.Apps -> Dock(
                        favorites = favorites,
                        config = config,
                        iconBitmaps = iconBitmaps,
                        density = density,
                        interactive = interactive && isActive,
                        onLaunch = onLaunch
                    )
                    WidgetPage.Calendar -> CalendarNotchPage(
                        state = calendarState,
                        refreshing = calendarRefreshing,
                        // Only the page under the cursor may own the hit path: an alpha-0 page
                        // would otherwise swallow clicks meant for the page beneath it.
                        interactive = interactive && isActive
                    )
                    WidgetPage.Weather -> WeatherNotchPage(
                        state = weatherState ?: WeatherState.Loading,
                        fetching = weatherFetching,
                        fahrenheit = fahrenheit,
                        // The retry chip is the only pointer target here, gated
                        // like every other page's controls (pitfall 24).
                        interactive = interactive && isActive,
                        onRefresh = onRefreshWeather
                    )
                    // The whole page is the click target: hand off to the OS
                    // task inspector (see SystemNotchPage).
                    WidgetPage.System -> SystemNotchPage(
                        system, interactive && isActive, onOpenSystemMonitor
                    )
                    WidgetPage.Music -> if (session != null) {
                        MusicPage(
                            session = session,
                            media = media,
                            battery = battery,
                            volumePercent = volumePercent,
                            volumeMuted = volumeMuted,
                            interactive = interactive && isActive,
                            discModifier = discModifier,
                            titleModifier = titleModifier,
                            volumeRingModifier = volumeRingModifier,
                            batteryRingModifier = batteryRingModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPage(
    session: MediaSession,
    media: MediaController,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    volumeMuted: Boolean?,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MusicContent(
            session = session,
            media = media,
            battery = battery,
            volumePercent = volumePercent,
            volumeMuted = volumeMuted,
            interactive = interactive,
            discModifier = discModifier,
            titleModifier = titleModifier,
            volumeRingModifier = volumeRingModifier,
            batteryRingModifier = batteryRingModifier
        )
    }
}

/**
 * Vinyl left in an album-tinted halo with a live dot, metadata in the middle over a thicker
 * playhead, rings above a roomier transport cluster; the title block crossfades on track change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicContent(
    session: MediaSession,
    media: MediaController,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    volumeMuted: Boolean?,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier
) {
    val playing = session.state == PlaybackState.PLAYING
    val nowMs = rememberMediaNowMs(playing)
    val duration = session.durationMs.coerceAtLeast(0L)
    val elapsed = session.displayedPositionMs(nowMs).coerceAtLeast(0L)
        .coerceAtMost(if (duration > 0L) duration else Long.MAX_VALUE)
    // Subtitle joins artist and album; either half alone reads fine, a track with neither stays one line.
    val subtitle = remember(session.artist, session.album) {
        listOf(session.artist, session.album)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" — ")
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The vinyl slot swaps records on a track change: keyed on the artwork, so the entrance
        // fires when the next cover decodes and a cover that lands a poll late still gets one.
        Box(
            modifier = discModifier.size(62.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient halo tinted by the cover's dominant colour (accent cyan with no cover).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background((session.artTint ?: LiveAccent).copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            )
            AnimatedContent(
                targetState = session.artwork,
                transitionSpec = {
                    // Both covers travel left, the old one out through the clipped island edge and
                    // the new in from beyond it; offset/alpha/scale only, so SOFTWARE never relayouts.
                    (slideInHorizontally(
                        initialOffsetX = { -it * 3 },
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 130f,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 130f)
                    ) + fadeIn(tween(260, delayMillis = 120))) togetherWith (
                        slideOutHorizontally(
                            targetOffsetX = { -it * 3 },
                            animationSpec = tween(460, easing = FastOutSlowInEasing)
                        ) + scaleOut(
                            targetScale = 0.85f,
                            animationSpec = tween(460, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(240, delayMillis = 220))
                    )
                },
                label = "vinylSwap"
            ) { art ->
                VinylDisc(
                    art = art,
                    playing = playing,
                    size = 56.dp,
                    // null while inactive: the clickable modifier only exists when onClick != null,
                    // so an inactive page contributes no hit node.
                    onClick = if (interactive) {
                        { AppLauncher.openMediaProvider(session.sourceId, session.source) }
                    } else {
                        null
                    }
                )
            }
            // Live dot overlapping the halo (no layout cost): accent while the record turns, dimmed on pause.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (playing) LiveAccent
                        else Color.White.copy(alpha = 0.30f)
                    )
                    .border(2.dp, Color(0xFF0B0D12).copy(alpha = 0.9f), CircleShape)
            )
        }

        // Title block floats above the progress strip, which hugs the bottom edge to share the
        // transport cluster's baseline.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Keyed on the track identity (not the bitmap): the words glide
                // on every song change even when the cover arrives a poll late.
                AnimatedContent(
                    targetState = session.contentId,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 8 })
                    },
                    label = "trackTextSwap"
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MarqueeText(
                            text = session.title,
                            color = Color.White.copy(alpha = 0.96f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = titleModifier
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrackTimeLabel(elapsed, frameHeight = 22.dp)
                TrackProgress(
                    fraction = session.progressFraction(nowMs),
                    durationMs = duration,
                    seekable = media.supportsSeek,
                    enabled = interactive,
                    onSeek = media::seekTo,
                    modifier = Modifier.weight(1f),
                    accent = session.artTint ?: LiveAccent
                )
                TrackTimeLabel(duration, frameHeight = 22.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Both rings reuse the quickview's shapes so volume and battery read the same everywhere;
        // the fixed stack of 30 + 4 + 36 + 4 = 74dp keeps the minimum island depth fitting.
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (volumePercent != null) {
                    // The expanded ring doubles as the mute toggle: one click flips the OS mute and
                    // the ring empties to level 0 with a dimmed glyph.
                    val mutedNow = volumeMuted == true
                    val muteLabel = stringResource(
                        if (mutedNow) Res.string.music_unmute else Res.string.music_mute
                    )
                    Box(
                        modifier = volumeRingModifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .then(
                                if (interactive) {
                                    Modifier.clickable(role = Role.Button) { SystemVolume.toggleMute() }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        LevelRing(
                            level = if (mutedNow) 0 else volumePercent,
                            icon = volumePainter(),
                            contentDescription = muteLabel,
                            modifier = Modifier.size(30.dp),
                            tint = if (mutedNow) LevelRingGlyph.copy(alpha = 0.4f) else LevelRingGlyph,
                            ringColor = if (mutedNow) LevelRingGlyph.copy(alpha = 0.3f) else LevelRingGlyph
                        )
                    }
                }
                if (battery != null) {
                    LevelRing(
                        level = battery.levelPercent,
                        icon = batteryPainter(battery.isAudio),
                        badgeCount = battery.connectedCount.takeIf { it > 1 },
                        contentDescription = stringResource(
                            Res.string.music_battery,
                            battery.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.music_audio_device)
                        ),
                        modifier = batteryRingModifier.size(30.dp),
                        lowTint = BatteryLowTint,
                        lowThreshold = BatteryLowThreshold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TransportControls(
                state = session.state,
                enabled = interactive,
                media = media,
                accent = session.artTint ?: LiveAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


/**
 * Warms the shared icon cache so the dock never shows a wall of shimmering placeholders.
 */
@Composable
internal fun LoadFavoriteIcons(
    favorites: List<AppItem>,
    iconBitmaps: MutableMap<String, ImageBitmap>
) {
    LaunchedEffect(favorites) {
        favorites.forEach { app ->
            if (app.id !in iconBitmaps) {
                launch { iconBitmaps[app.id] = AppIconLoader.loadIcon(app) }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Dock(
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    density: Density,
    interactive: Boolean,
    onLaunch: (AppItem) -> Unit
) {
    // Pointer position and icon centres share the icon container's local pixel
    // space, so the fisheye tracks the cursor with exact accuracy.
    val centers = remember { mutableStateMapOf<Int, Float>() }
    val pointer = remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(interactive) {
        if (!interactive) pointer.value = null
    }
    val radiusPx = with(density) { config.baseIconSizeDp.dp.toPx() * FisheyeRadiusFactor }
    val liftPx = with(density) { IconLift.toPx() }

    // Held as State (not delegated) so only the label reads it: crossing between
    // icons recomposes the label alone, never the whole dock.
    val closestName: State<String?> = remember(favorites, radiusPx) {
        derivedStateOf {
            val p = pointer.value ?: return@derivedStateOf null
            var bestDistance = radiusPx
            var name: String? = null
            favorites.forEachIndexed { index, app ->
                val center = centers[index] ?: return@forEachIndexed
                val distance = abs(p - center)
                if (distance < bestDistance) {
                    bestDistance = distance
                    name = app.name
                }
            }
            name
        }
    }

    // The dock stays in the tree while hidden, so pointer tracking and clicks must be gated:
    // an invisible dock must not swallow events meant for the quickview.
    val pointerModifier = if (interactive) {
        Modifier
            .onPointerEvent(PointerEventType.Move) { change ->
                pointer.value = change.changes.firstOrNull()?.position?.x
            }
            .onPointerEvent(PointerEventType.Exit) { pointer.value = null }
    } else {
        // Hidden dock: no tracking, and child clickables are disabled below,
        // so events fall through untouched.
        Modifier
    }

    val icons: @Composable () -> Unit = {
        favorites.forEachIndexed { index, app ->
            DockIcon(
                app = app,
                iconBitmap = iconBitmaps[app.id],
                center = centers[index],
                pointer = pointer,
                radiusPx = radiusPx,
                peakScale = config.dockScaleFactor,
                liftPx = liftPx,
                baseSizeDp = config.baseIconSizeDp,
                enabled = interactive,
                onPositioned = { centers[index] = it },
                onClick = { onLaunch(app) }
            )
        }
    }

    // Centred on the depth axis so no dead strip piles up under the icons or above the label,
    // and the label's descenders stay clear of the window border.
    Column(
        modifier = Modifier.fillMaxSize().padding(dockPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (config.showTooltips) DockLabel(closestName, config)
        Row(
            modifier = pointerModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) { icons() }
    }
}

/**
 * Insets the dock away from the top screen edge, with extra room on the inner edge for the
 * magnification lift.
 */
private fun dockPadding(): PaddingValues =
    PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)

@Composable
private fun DockIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    center: Float?,
    pointer: State<Float?>,
    radiusPx: Float,
    peakScale: Float,
    liftPx: Float,
    baseSizeDp: Int,
    enabled: Boolean,
    onPositioned: (Float) -> Unit,
    onClick: () -> Unit
) {
    // Read the pointer here, not in the dock body, so recomposition stays local to each icon;
    // derivedStateOf drops frames whose scale is unchanged (icons outside the radius).
    val targetScale by remember(enabled, center, radiusPx, peakScale) {
        derivedStateOf {
            if (enabled) fisheyeScale(pointer.value, center, radiusPx, peakScale) else 1f
        }
    }
    val animatedScale by animateFloatAsState(targetScale, iconSpring(), label = "dockScale")
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .padding(DockLayout.IconPadding)
            .size(baseSizeDp.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInParent()
                onPositioned(position.x + coordinates.size.width / 2f)
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                // Magnified icons lift toward the screen centre (down for a
                // top-centred notch).
                translationY = (animatedScale - 1f) * liftPx
            }
            .clip(RoundedCornerShape(11.dp))
            .then(
                // Only install the clickable while interactive: clickable(enabled = false) still
                // hit-tests, so a disabled icon would occupy the hit path of the page beneath.
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = iconBitmap,
            animationSpec = tween(220),
            label = "dockIconFade"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Shimmer while the icon loads in the background, so a cold dock
                // never flashes a wall of empty grey squares.
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(11.dp)
                )
            }
        }
    }
}

@Composable
private fun DockLabel(nameState: State<String?>, config: NotchConfig) {
    val labelSize = (config.expandedNotchHeightDp * DockLabelFontSizeFactor).sp
    val lineHeight = (labelSize.value * 1.15f).sp
    // The slot is always one line tall so icons never shift when the label appears; the snug
    // line height keeps descenders clear of the border.
    val slotHeight = with(LocalDensity.current) { lineHeight.toDp() }
    Box(
        modifier = Modifier
            .heightIn(min = slotHeight)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nameState.value.orEmpty(),
            color = TextPrimary.copy(alpha = 0.9f),
            fontSize = labelSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
