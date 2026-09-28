package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
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
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.music_audio_device
import com.starkdroid.quicknotch.generated.resources.music_battery
import com.starkdroid.quicknotch.generated.resources.music_mute
import com.starkdroid.quicknotch.generated.resources.music_unmute
import com.starkdroid.quicknotch.generated.resources.music_volume
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.media.PlaybackState
import com.starkdroid.quicknotch.media.SystemVolume
import com.starkdroid.quicknotch.model.AppItem

import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.theme.TextPrimary
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

// ── Widget pages ─────────────────────────────────────────────────────────────

internal enum class WidgetPage { Apps, Music }

/**
 * Renders every active widget page as an always-composed sibling so the pager
 * never measures the music page while the window is animating. Inactive pages
 * are pushed aside with a small alpha+slide layer transform (never a
 * subtraction/recomposition), and only the page under the pointer receives
 * input.
 */
@Composable
internal fun WidgetPages(
    pages: List<WidgetPage>,
    pagePosition: Float,
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    session: MediaSession?,
    media: MediaController,
    nowMs: Long,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    volumeMuted: Boolean?,
    density: Density,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier,
    onLaunch: (AppItem) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        pages.forEachIndexed { index, page ->
            val relative = index - pagePosition
            val isActive = abs(relative) < 0.5f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
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
                    WidgetPage.Music -> if (session != null) {
                        MusicPage(
                            session = session,
                            media = media,
                            nowMs = nowMs,
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
    nowMs: Long,
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
            nowMs = nowMs,
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
 * Art pinned to the left end of the strip, the metadata held in the middle,
 * the progress/timestamps strip hugging the bottom edge, and the level rings
 * stacked above the transport buttons on the right — the two clusters drift
 * slightly toward the bottom so the controls read as a settled base under the
 * text. Every transport control stays visible here; collapsing the play/pause
 * only affects the slim quickview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicContent(
    session: MediaSession,
    media: MediaController,
    nowMs: Long,
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
    val duration = session.durationMs.coerceAtLeast(0L)
    val elapsed = session.displayedPositionMs(nowMs).coerceAtLeast(0L)
        .coerceAtMost(if (duration > 0L) duration else Long.MAX_VALUE)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // The vinyl slot morphs between pill and page (discModifier on the
        // slot), and swaps records on a track change: the old cover slides out
        // through the notch's left edge (the Row above clips at the island
        // bounds) and the new one slides in from the left with a springy
        // settle. Keyed on the artwork itself, so the entrance fires exactly
        // when the next cover has decoded — a cover that lands a poll late
        // gets its own entrance instead of popping in.
        Box(
            modifier = discModifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = session.artwork,
                transitionSpec = {
                    // A record-swap conveyor: both covers travel LEFT, so the
                    // eye reads one continuous motion — the old disc glides
                    // out through the notch's left edge (the Row above clips
                    // at the island bounds) while the new one slides in from
                    // beyond it and settles with a soft spring. Travel is 3x
                    // the disc width so the exit/enter are unmistakable, the
                    // exit fades only in its last third (the cover stays
                    // readable while it travels), and everything animates
                    // offset/alpha/scale — all graphicsLayer-backed, so the
                    // SOFTWARE renderer never relayouts mid-swap.
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
                    onClick = {
                        AppLauncher.openMediaProvider(session.sourceId, session.source)
                    }
                )
            }
        }

        // Title/artist float in the space above the progress strip, and the
        // strip hugs the bottom edge — mirroring the transport cluster's own
        // bottom bias so both flanks settle at the same baseline.
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MarqueeText(
                        text = session.title,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = titleModifier
                    )
                    if (session.artist.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = session.artist,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackTimeLabel(elapsed)
                TrackProgress(
                    fraction = session.progressFraction(nowMs),
                    durationMs = duration,
                    seekable = media.supportsSeek,
                    enabled = interactive,
                    onSeek = media::seekTo,
                    modifier = Modifier.weight(1f)
                )
                TrackTimeLabel(duration)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Level indicators sit on top of the media controls, with the whole
        // cluster riding near the bottom edge. Both rings use the exact same
        // shapes as the quickview's, so volume and battery read
        // identically everywhere — the collapsed-only toggles govern just the
        // pill.
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (volumePercent != null) {
                    // The expanded volume ring doubles as the mute toggle: one
                    // click flips the OS device mute, and the ring empties to
                    // level 0 (with a dimmed glyph) while muted so the state
                    // reads at a glance. The sharedElement modifier rides the
                    // wrapping Box, so the morph bounds stay the ring's 28dp.
                    val mutedNow = volumeMuted == true
                    val muteLabel = stringResource(
                        if (mutedNow) Res.string.music_unmute else Res.string.music_mute
                    )
                    Box(
                        modifier = volumeRingModifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { SystemVolume.toggleMute() },
                        contentAlignment = Alignment.Center
                    ) {
                        LevelRing(
                            level = if (mutedNow) 0 else volumePercent,
                            icon = volumePainter(),
                            contentDescription = muteLabel,
                            modifier = Modifier.size(28.dp),
                            tint = if (mutedNow) LevelRingGlyph.copy(alpha = 0.4f) else LevelRingGlyph,
                            ringColor = if (mutedNow) LevelRingGlyph.copy(alpha = 0.3f) else LevelRingGlyph
                        )
                    }
                }
                if (battery != null) {
                    LevelRing(
                        level = battery.levelPercent,
                        icon = batteryPainter(battery.isAudio),
                        contentDescription = stringResource(
                            Res.string.music_battery,
                            battery.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.music_audio_device)
                        ),
                        modifier = batteryRingModifier.size(28.dp),
                        lowTint = BatteryLowTint,
                        lowThreshold = BatteryLowThreshold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TransportControls(
                state = session.state,
                enabled = interactive,
                media = media
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Dock (fisheye app launcher) ──────────────────────────────────────────────

/**
 * Warms the shared icon cache for the pinned list so the dock never shows a
 * wall of shimmering placeholders after a cold start.
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

    // The dock lives in the tree even while hidden, so pointer tracking and icon
    // clicks must be gated explicitly: an invisible dock must not swallow events
    // meant for the quickview or launch apps from under the cursor.
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

    // Centred along the depth axis instead of pinned to the anchored edge:
    // that removes the dead strip that used to pile up under the icons
    // (labels off) or above the label (labels on), and keeps the label's
    // descenders clear of the window border.
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
 * Insets the dock away from the top screen edge so icons never touch the
 * display border, and leaves extra room on the inner edge for the
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
    // Reading the pointer here (not in the dock body) keeps recomposition local
    // to each icon as the cursor glides across the dock.
    val targetScale = if (enabled) fisheyeScale(pointer.value, center, radiusPx, peakScale) else 1f
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
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
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
    // The slot is always as tall as one line even with no name showing, so the
    // icons never shift when the label appears; the snug line height plus the
    // vertical padding guarantee descenders clear the window border.
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
