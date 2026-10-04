package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.AccentSoft
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.NotchBorder
import com.starkdroid.quicknotch.theme.SettingsBg
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsBorderStrong
import com.starkdroid.quicknotch.theme.SettingsCard
import com.starkdroid.quicknotch.theme.SettingsCardHover
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.SettingsSelected
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import org.jetbrains.compose.resources.stringResource
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.calendar
import com.starkdroid.quicknotch.generated.resources.onboarding_apps_subtitle
import com.starkdroid.quicknotch.generated.resources.onboarding_apps_title
import com.starkdroid.quicknotch.generated.resources.onboarding_calendar_note
import com.starkdroid.quicknotch.generated.resources.onboarding_calendar_subtitle
import com.starkdroid.quicknotch.generated.resources.onboarding_calendar_title
import com.starkdroid.quicknotch.generated.resources.onboarding_continue
import com.starkdroid.quicknotch.generated.resources.onboarding_feature_apps
import com.starkdroid.quicknotch.generated.resources.onboarding_feature_calendar
import com.starkdroid.quicknotch.generated.resources.onboarding_feature_media
import com.starkdroid.quicknotch.generated.resources.onboarding_back
import com.starkdroid.quicknotch.generated.resources.onboarding_feature_quiet
import com.starkdroid.quicknotch.generated.resources.onboarding_welcome_subtitle
import com.starkdroid.quicknotch.generated.resources.onboarding_welcome_title
import com.starkdroid.quicknotch.generated.resources.onboarding_finish
import com.starkdroid.quicknotch.generated.resources.onboarding_skip
import com.starkdroid.quicknotch.generated.resources.onboarding_step_of
import com.starkdroid.quicknotch.generated.resources.onboarding_style_subtitle
import com.starkdroid.quicknotch.generated.resources.onboarding_style_title
import com.starkdroid.quicknotch.generated.resources.style_default
import com.starkdroid.quicknotch.generated.resources.style_drag_handle
import com.starkdroid.quicknotch.generated.resources.style_floating_pill

/**
 * The first-run onboarding: four sequential screens -- welcome, pill style,
 * Google Calendar, pinned apps -- walked one after the other, each skippable,
 * and all of them styling a live preview of the notch carried between screens.
 * The real overlay does not exist until the flow ends: the user is configuring
 * the pill that would otherwise already be on screen.
 *
 * Every choice applies to the config immediately, so skipping a later step
 * keeps the earlier ones -- "skip" means "I'll fine-tune in Settings", which is
 * also why each screen says exactly that.
 */
private enum class OnboardingStep(val index: Int) {
    Welcome(0),
    Style(1),
    Calendar(2),
    Apps(3);

    /** Where the carried notch parks, how big it is, per step. */
    val islandOffsetY: Dp
        get() = when (this) {
            Welcome, Style -> 4.dp
            Calendar -> 40.dp
            Apps -> 52.dp
        }
}

private const val SAMPLE_TICKER = "Standup with the platform team"
private const val SAMPLE_COUNT = 2

@Composable
internal fun OnboardingScreen(
    repository: FavoritesRepository
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val calendarState by GoogleCalendar.state.collectAsState()
    val calendarRefreshing by GoogleCalendar.refreshing.collectAsState()
    val calendarTick by GoogleCalendar.minuteTick.collectAsState()

    var step by remember { mutableStateOf(OnboardingStep.Welcome) }

    // The apps list is only needed on the last screen, so the (sometimes slow)
    // installed-apps scan starts when the user gets there, not at launch.
    var scannedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(step) {
        if (step == OnboardingStep.Apps && scannedApps.isEmpty()) {
            isScanning = true
            scannedApps = InstalledAppsScanner.scanAllInstalledApps()
            isScanning = false
        }
    }

    fun finish() {
        repository.updateConfig { it.copy(onboardingDone = true) }
    }

    fun advance() {
        step = when (step) {
            OnboardingStep.Welcome -> OnboardingStep.Style
            OnboardingStep.Style -> OnboardingStep.Calendar
            OnboardingStep.Calendar -> OnboardingStep.Apps
            OnboardingStep.Apps -> step // finished via [finish]
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg)
            .padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thin segmented progress: one solid segment per step, filled up to
        // and including the current one. No gradients — flat fills only.
        OnboardingProgress(current = step)
        Spacer(modifier = Modifier.height(16.dp))

        // The notch lives OUTSIDE the step transition inside a solid stage
        // card, so it is one always-composed element that springs in size and
        // position as the steps change. A shared element between two fading
        // screens reads as a dissolve; a single element travelling under its
        // own spring reads as the carry this flow is about.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SettingsCard)
                .border(1.dp, SettingsBorder, RoundedCornerShape(18.dp))
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(112.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val islandWidth by animateDpAsState(
                    targetValue = islandWidthFor(step, favorites.size),
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                    label = "onboarding-island-width"
                )
                val islandHeight by animateDpAsState(
                    targetValue = islandHeightFor(step, config.style),
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                    label = "onboarding-island-height"
                )
                val islandY by animateDpAsState(
                    targetValue = step.islandOffsetY,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                    label = "onboarding-island-y"
                )
                val expanded = step == OnboardingStep.Apps
                val shape = notchShape(expanded = expanded, style = config.style)
                Box(
                    modifier = Modifier
                        .offset(y = islandY)
                        .width(islandWidth)
                        .height(islandHeight)
                        .clip(shape)
                        .islandSurface(config.surface)
                        .border(1.dp, NotchBorder, shape),
                    contentAlignment = Alignment.Center
                ) {
                    NotchPreviewContent(step = step, style = config.style, surface = config.surface, favorites = favorites)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // Directional: forward enters from the right, back
                // enters from the left, so walking backwards through
                // the flow feels like unwinding it.
                val forward = targetState.index > initialState.index
                if (forward) {
                    (slideInHorizontally(tween(360)) { it / 3 } + fadeIn(tween(360))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(360)) { -it / 3 } + fadeIn(tween(360))) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "onboarding-copy",
            modifier = Modifier.weight(1f, fill = true)
        ) { stepForCopy ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (stepForCopy) {
                    OnboardingStep.Welcome -> {
                        OnboardingHeading(
                            title = stringResource(Res.string.onboarding_welcome_title),
                            subtitle = stringResource(Res.string.onboarding_welcome_subtitle)
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        // Two-by-two feature cards with staggered entrances.
                        Column(
                            modifier = Modifier.width(460.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TabEntrance(index = 0) {
                                        FeatureCard(
                                            Icons.AutoMirrored.Filled.Launch,
                                            stringResource(Res.string.onboarding_feature_apps)
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    TabEntrance(index = 1) {
                                        FeatureCard(
                                            Icons.Default.MusicNote,
                                            stringResource(Res.string.onboarding_feature_media)
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TabEntrance(index = 2) {
                                        FeatureCard(
                                            null,
                                            stringResource(Res.string.onboarding_feature_calendar)
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    TabEntrance(index = 3) {
                                        FeatureCard(
                                            Icons.Default.VisibilityOff,
                                            stringResource(Res.string.onboarding_feature_quiet)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OnboardingStep.Style -> {
                        OnboardingHeading(
                            title = stringResource(Res.string.onboarding_style_title),
                            subtitle = stringResource(Res.string.onboarding_style_subtitle)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        StyleCards(
                            selected = config.style,
                            onSelect = { style ->
                                repository.updateConfig { it.copy(style = style) }
                            }
                        )
                    }
                    OnboardingStep.Calendar -> {
                        OnboardingHeading(
                            title = stringResource(Res.string.onboarding_calendar_title),
                            subtitle = stringResource(Res.string.onboarding_calendar_subtitle)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        // The same widget the Widgets tab renders, inside a
                        // solid card; the notch above shows the sample
                        // integration.
                        Box(
                            modifier = Modifier
                                .width(520.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SettingsCard)
                                .border(1.dp, SettingsBorder, RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            CalendarWidget(
                                state = calendarState,
                                refreshing = calendarRefreshing,
                                tick = calendarTick,
                                showInQuickView = config.widgets.showCalendar,
                                onShowInQuickViewChange = { enabled ->
                                    repository.updateConfig {
                                        it.copy(widgets = it.widgets.copy(showCalendar = enabled))
                                    }
                                },
                                onConnect = GoogleCalendar::connect,
                                onCancel = GoogleCalendar::cancelConnect,
                                onDisconnect = GoogleCalendar::disconnect,
                                onRefresh = GoogleCalendar::refresh
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.onboarding_calendar_note),
                            style = AppTypography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                    OnboardingStep.Apps -> {
                        OnboardingHeading(
                            title = stringResource(Res.string.onboarding_apps_title),
                            subtitle = stringResource(Res.string.onboarding_apps_subtitle)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth()
                        ) {
                            PinnedAppsTab(
                                favorites = favorites,
                                scannedApps = scannedApps,
                                searchQuery = searchQuery,
                                isScanning = isScanning,
                                onSearchQueryChange = { searchQuery = it },
                                onAddFavorite = repository::addFavorite,
                                onRemoveFavorite = repository::removeFavorite,
                                onMove = repository::reorderFavorites,
                                onResetDefaults = {},
                                fixedHeight = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OnboardingButtons(
            step = step,
            onBack = {
                if (step != OnboardingStep.Welcome) {
                    step = when (step) {
                        OnboardingStep.Style -> OnboardingStep.Welcome
                        OnboardingStep.Calendar -> OnboardingStep.Style
                        else -> OnboardingStep.Calendar
                    }
                }
            },
            onSkip = {
                if (step == OnboardingStep.Apps) finish() else advance()
            },
            onContinue = {
                if (step == OnboardingStep.Apps) finish() else advance()
            }
        )
    }
}

/** Segmented progress: flat solid fills, current + done in accent. */
@Composable
private fun OnboardingProgress(current: OnboardingStep) {
    Row(
        modifier = Modifier.width(460.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnboardingStep.entries.forEach { step ->
            val filled = step.index <= current.index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (filled) AccentSolid else SettingsBorder)
            )
        }
    }
}

/** The carried notch's size per step; the dock's width grows with the pins. */
private fun islandWidthFor(step: OnboardingStep, pinnedCount: Int): Dp = when (step) {
    OnboardingStep.Welcome, OnboardingStep.Style -> 150.dp
    OnboardingStep.Calendar -> 236.dp
    OnboardingStep.Apps -> (DockLayout.ContentPadding * 2 +
        DockLayout.iconSlot(30.dp) * pinnedCount.coerceAtLeast(1)).coerceAtLeast(160.dp)
}

private fun islandHeightFor(step: OnboardingStep, style: NotchStyle): Dp = when {
    step == OnboardingStep.Apps -> 56.dp
    // Collapsed drag handle: the slim 8dp bar, exactly as the real pill sits.
    style == NotchStyle.DRAG_HANDLE -> DockLayout.DragHandleHeightDp.dp
    else -> 30.dp
}

/**
 * What the carried notch shows, per step. The collapsed drag handle shows only
 * its grabber line -- matching QuickView exactly -- in every step it is
 * collapsed for.
 */
@Composable
private fun NotchPreviewContent(
    step: OnboardingStep,
    style: NotchStyle,
    surface: NotchSurface,
    favorites: List<AppItem>
) {
    if (step != OnboardingStep.Apps && style == NotchStyle.DRAG_HANDLE) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(handleTint(surface))
        )
        return
    }
    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
        label = "onboarding-notch-content",
        contentKey = { it }
    ) { stepForContent ->
        when (stepForContent) {
            OnboardingStep.Welcome, OnboardingStep.Style -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "9:41",
                    style = AppTypography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                CalendarDayIndicator(count = SAMPLE_COUNT)
            }
            OnboardingStep.Calendar -> Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarqueeText(
                    text = SAMPLE_TICKER,
                    color = TextPrimary,
                    fontSize = AppTypography.bodySmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                CalendarDayIndicator(count = SAMPLE_COUNT)
            }
            OnboardingStep.Apps -> OnboardingDockRow(favorites = favorites)
        }
    }
}

/** The pinned apps, live inside the carried notch while the list is edited. */
@Composable
private fun OnboardingDockRow(favorites: List<AppItem>) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (favorites.isEmpty()) {
            Text(
                text = "· · ·",
                style = AppTypography.titleMedium,
                color = TextMuted
            )
        }
        favorites.forEach { app ->
            var icon by remember(app.id) { mutableStateOf(AppIconLoader.getCachedIcon(app.id)) }
            LaunchedEffect(app.id) {
                icon = AppIconLoader.loadIcon(app)
            }
            val bitmap = icon
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = app.name,
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(TextMuted.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

/**
 * Three style cards: solid surfaces with hover lift and spring press, selected
 * gets the accent border + soft tint. Each shows the pill with sample content.
 */
@Composable
private fun StyleCards(
    selected: NotchStyle,
    onSelect: (NotchStyle) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NotchStyle.entries.forEachIndexed { index, style ->
            val isSelected = style == selected
            TabEntrance(index = index) {
                val hoverPress = rememberHoverPressState()
                val hovered = hoverPress.hovered
                val pressed = hoverPress.pressed
                val scale = hoverPressScale(hoverPress, pressedScale = 0.97f)
                val lift by animateDpAsState(
                    targetValue = if (hovered && !pressed) (-2).dp else 0.dp,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                    label = "styleCardLift"
                )
                Column(
                    modifier = Modifier
                        .width(188.dp)
                        .offset(y = lift)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) SettingsSelected else SettingsCard)
                        .border(
                            1.dp,
                            when {
                                isSelected -> AccentSolid.copy(alpha = 0.7f)
                                hovered -> SettingsBorderStrong
                                else -> SettingsBorder
                            },
                            RoundedCornerShape(16.dp)
                        )
                        .trackHoverPress(hoverPress)
                        .plainClickable(onClick = { onSelect(style) })
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The pill itself, in this style, with a sample quickview row
                    // -- except the drag handle, whose collapsed design is a slim
                    // grabber bar with nothing else in it (QuickView draws exactly
                    // that), so this card previews it honestly.
                    Box(
                        modifier = Modifier
                            .height(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (style == NotchStyle.DRAG_HANDLE) {
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(DockLayout.DragHandleHeightDp.dp)
                                    .clip(notchShape(expanded = false, style = style))
                                    .islandSurface(NotchSurface.SOLID)
                                    .border(1.dp, NotchBorder, notchShape(expanded = false, style = style)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(handleTint(NotchSurface.SOLID))
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(30.dp)
                                    .clip(notchShape(expanded = false, style = style))
                                    .islandSurface(NotchSurface.SOLID)
                                    .border(1.dp, NotchBorder, notchShape(expanded = false, style = style)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "9:41",
                                        style = AppTypography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    CalendarDayIndicator(count = SAMPLE_COUNT)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            when (style) {
                                NotchStyle.DYNAMIC_ISLAND -> Res.string.style_default
                                NotchStyle.FLOATING_PILL -> Res.string.style_floating_pill
                                NotchStyle.DRAG_HANDLE -> Res.string.style_drag_handle
                            }
                        ),
                        style = AppTypography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(AccentSoft)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "●",
                                style = AppTypography.labelSmall,
                                color = AccentSolid,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One "what the app can do" card: icon chip over a short promise on a solid
 * surface. Grid siblings share the same height via weight.
 */
@Composable
private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsCard)
            .border(1.dp, SettingsBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(SettingsElevated)
                .border(1.dp, SettingsBorder, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentSolid,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Icon(
                    painter = org.jetbrains.compose.resources.painterResource(Res.drawable.calendar),
                    contentDescription = null,
                    tint = AccentSolid,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            style = AppTypography.bodyMedium,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun OnboardingHeading(title: String, subtitle: String) {
    Text(
        text = title,
        style = AppTypography.headlineSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        color = TextPrimary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = AppTypography.bodySmall,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun OnboardingButtons(
    step: OnboardingStep,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (step != OnboardingStep.Welcome) {
            val backHover = rememberHoverPressState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (backHover.hovered) SettingsCardHover else Color.Transparent)
                    .trackHoverPress(backHover)
                    .plainClickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.onboarding_back),
                        style = AppTypography.labelLarge,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = stringResource(
                Res.string.onboarding_step_of,
                (step.index + 1).toString(),
                OnboardingStep.entries.size.toString()
            ),
            style = AppTypography.labelSmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.weight(1f))
        GhostButton(
            text = stringResource(Res.string.onboarding_skip),
            onClick = onSkip
        )
        Spacer(modifier = Modifier.width(8.dp))
        PrimaryButton(
            text = stringResource(
                if (step == OnboardingStep.Apps) Res.string.onboarding_finish
                else Res.string.onboarding_continue
            ),
            onClick = onContinue
        )
    }
}
