package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.AccentIndigoLight
import com.starkdroid.quicknotch.theme.NotchBorder
import com.starkdroid.quicknotch.theme.AppTypography
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
 * The first-run onboarding: three sequential screens -- pill style, Google
 * Calendar, pinned apps -- walked one after the other, each skippable, and all
 * of them styling a live preview of the notch that is carried between screens
 * as a shared element. The real overlay does not exist until the flow ends:
 * the user is configuring the pill that would otherwise already be on screen.
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
}

private const val SAMPLE_TICKER = "Standup with the platform team"
private const val SAMPLE_COUNT = 2

@OptIn(ExperimentalSharedTransitionApi::class)
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

    SharedTransitionLayout {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // Forward-only flow: each screen enters from the right while
                // the carried notch morphs between them.
                (slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(220)))
            },
            label = "onboarding-step"
        ) { currentStep ->
            val animatedContentScope = this
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FlatBg)
                    .padding(horizontal = 48.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingNotchPreview(
                    step = currentStep,
                    style = config.style,
                    surface = config.surface,
                    favorites = favorites,
                    animatedContentScope = animatedContentScope,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(28.dp))
                StepDots(current = currentStep)
                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "onboarding-copy"
                ) { stepForCopy ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (stepForCopy) {
                            OnboardingStep.Welcome -> {
                                OnboardingHeading(
                                    title = stringResource(Res.string.onboarding_welcome_title),
                                    subtitle = stringResource(Res.string.onboarding_welcome_subtitle)
                                )
                                Spacer(modifier = Modifier.height(26.dp))
                                Column(
                                    modifier = Modifier.width(400.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    FeatureRow(Icons.Default.Launch, stringResource(Res.string.onboarding_feature_apps))
                                    FeatureRow(Icons.Default.MusicNote, stringResource(Res.string.onboarding_feature_media))
                                    FeatureRow(icon = null, label = stringResource(Res.string.onboarding_feature_calendar))
                                    FeatureRow(Icons.Default.VisibilityOff, stringResource(Res.string.onboarding_feature_quiet))
                                }
                            }
                            OnboardingStep.Style -> {
                                OnboardingHeading(
                                    title = stringResource(Res.string.onboarding_style_title),
                                    subtitle = stringResource(Res.string.onboarding_style_subtitle)
                                )
                                Spacer(modifier = Modifier.height(22.dp))
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
                                Spacer(modifier = Modifier.height(20.dp))
                                // The same widget the Widgets tab renders; the
                                // notch above shows the sample integration.
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
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(Res.string.onboarding_calendar_note),
                                    style = AppTypography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            OnboardingStep.Apps -> {
                                OnboardingHeading(
                                    title = stringResource(Res.string.onboarding_apps_title),
                                    subtitle = stringResource(Res.string.onboarding_apps_subtitle)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.height(320.dp)) {
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

                Spacer(modifier = Modifier.weight(1f))
                OnboardingButtons(
                    step = currentStep,
                    onSkip = {
                        if (currentStep == OnboardingStep.Apps) finish() else advance()
                    },
                    onContinue = {
                        if (currentStep == OnboardingStep.Apps) finish() else advance()
                    }
                )
            }
        }
    }
}

/** The notch itself, carried between steps as one shared element. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.OnboardingNotchPreview(
    step: OnboardingStep,
    style: NotchStyle,
    surface: NotchSurface,
    favorites: List<AppItem>,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier
) {
    val islandWidth = when (step) {
        OnboardingStep.Welcome, OnboardingStep.Style -> 150.dp
        OnboardingStep.Calendar -> 236.dp
        OnboardingStep.Apps -> (DockLayout.ContentPadding * 2 +
            DockLayout.iconSlot(30.dp) * favorites.size.coerceAtLeast(1)).coerceAtLeast(160.dp)
    }
    // The drag handle collapses to a slim 8dp bar, not the taller pill -- and
    // the preview must lie about that no more than about anything else.
    val islandHeight = when {
        step == OnboardingStep.Apps -> 56.dp
        style == NotchStyle.DRAG_HANDLE -> DockLayout.DragHandleHeightDp.dp
        else -> 30.dp
    }

    Box(
        modifier = modifier
            .sharedElement(
                rememberSharedContentState(key = "onboarding-notch"),
                animatedVisibilityScope = animatedContentScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.9f, stiffness = 320f) }
            )
            .width(islandWidth)
            .height(islandHeight)
            .clip(notchShape(expanded = step == OnboardingStep.Apps, style = style))
            .islandSurface(surface)
            .border(
                1.dp,
                NotchBorder,
                notchShape(expanded = step == OnboardingStep.Apps, style = style)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "onboarding-notch-content"
        ) { stepForContent ->
            // Collapsed drag handle: a single slim grabber line, the whole
            // point of the style -- no clock, no badges, matching QuickView.
            if (stepForContent != OnboardingStep.Apps && style == NotchStyle.DRAG_HANDLE) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(handleTint(surface))
                )
                return@AnimatedContent
            }
            when (stepForContent) {
                OnboardingStep.Welcome -> Row(
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
                OnboardingStep.Style -> Row(
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

/** Three style cards: bordered, unfilled, each showing the pill with sample content. */
@Composable
private fun StyleCards(
    selected: NotchStyle,
    onSelect: (NotchStyle) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        NotchStyle.entries.forEach { style ->
            val isSelected = style == selected
            Column(
                modifier = Modifier
                    .width(190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) FlatSurfaceSelected else FlatCard)
                    .border(
                        1.dp,
                        if (isSelected) AccentIndigoLight else FlatBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(style) }
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The pill itself, in this style, with a sample quickview row
                // -- except the drag handle, whose collapsed design is a slim
                // grabber bar with nothing else in it (QuickView draws exactly
                // that), so this card previews it honestly.
                Box(
                    modifier = Modifier
                        .height(30.dp),
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
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AccentIndigoLight else Color.Transparent)
                )
            }
        }
    }
}

/** One "what the app can do" line: an icon (the calendar glyph where a
 *  Material one would lie about the design) beside a short promise. */
@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FlatSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentIndigoLight,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    painter = org.jetbrains.compose.resources.painterResource(Res.drawable.calendar),
                    contentDescription = null,
                    tint = AccentIndigoLight,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = label,
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun OnboardingHeading(title: String, subtitle: String) {
    Text(
        text = title,
        style = AppTypography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = AppTypography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

/** Three dots; the carried notch is the real progress, this is just its caption. */
@Composable
private fun StepDots(current: OnboardingStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnboardingStep.entries.forEach { step ->
            Box(
                modifier = Modifier
                    .size(if (step == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (step == current) AccentIndigoLight else FlatBorder,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun OnboardingButtons(
    step: OnboardingStep,
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_skip),
                style = AppTypography.labelLarge,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AccentIndigoLight)
                .clickable(onClick = onContinue)
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(
                text = stringResource(
                    if (step == OnboardingStep.Apps) Res.string.onboarding_finish
                    else Res.string.onboarding_continue
                ),
                style = AppTypography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF14161F)
            )
        }
    }
}
