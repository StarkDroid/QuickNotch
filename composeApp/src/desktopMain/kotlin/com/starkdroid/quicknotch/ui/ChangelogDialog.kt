package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.AppVersion
import com.starkdroid.quicknotch.datasource.AppUpdates
import com.starkdroid.quicknotch.datasource.AppVersions
import com.starkdroid.quicknotch.datasource.Changelog
import com.starkdroid.quicknotch.datasource.ChangelogChange
import com.starkdroid.quicknotch.datasource.ChangelogRelease
import com.starkdroid.quicknotch.datasource.ChangelogStatus
import com.starkdroid.quicknotch.datasource.ProjectLinks
import com.starkdroid.quicknotch.datasource.UpdateState
import com.starkdroid.quicknotch.datasource.AppInstaller
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.about_copyright
import com.starkdroid.quicknotch.generated.resources.support_kofi
import com.starkdroid.quicknotch.generated.resources.view_source
import com.starkdroid.quicknotch.generated.resources.about_description
import com.starkdroid.quicknotch.generated.resources.about_product_name
import com.starkdroid.quicknotch.generated.resources.about_version
import com.starkdroid.quicknotch.generated.resources.app_name
import com.starkdroid.quicknotch.generated.resources.changelog_downloading
import com.starkdroid.quicknotch.generated.resources.changelog_empty
import com.starkdroid.quicknotch.generated.resources.changelog_error
import com.starkdroid.quicknotch.generated.resources.changelog_header
import com.starkdroid.quicknotch.generated.resources.changelog_loading
import com.starkdroid.quicknotch.generated.resources.changelog_no_asset
import com.starkdroid.quicknotch.generated.resources.changelog_restart_update
import com.starkdroid.quicknotch.generated.resources.changelog_retry
import com.starkdroid.quicknotch.generated.resources.changelog_update_available
import com.starkdroid.quicknotch.generated.resources.changelog_update_failed
import com.starkdroid.quicknotch.generated.resources.changelog_update_hint
import com.starkdroid.quicknotch.generated.resources.changelog_update_manual
import com.starkdroid.quicknotch.generated.resources.changelog_update_now
import com.starkdroid.quicknotch.generated.resources.changelog_update_ready
import com.starkdroid.quicknotch.generated.resources.changelog_up_to_date
import com.starkdroid.quicknotch.generated.resources.changelog_updating
import com.starkdroid.quicknotch.generated.resources.changelog_view_release
import com.starkdroid.quicknotch.generated.resources.close
import com.starkdroid.quicknotch.generated.resources.quicknotch_icon
import com.starkdroid.quicknotch.theme.AccentGreen
import com.starkdroid.quicknotch.theme.AccentRed
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsCard
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The changelog stops at the running version; offline it shows the
 * cached history rather than inventing an empty release.
 */
@Composable
internal fun ChangelogDialog(onDismiss: () -> Unit) {
    val feed by Changelog.feed.collectAsState()
    val status by Changelog.status.collectAsState()
    val update by AppUpdates.state.collectAsState()

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        AppUpdates.check()
    }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "changelogScale"
    )

    // Only the versions this build has actually lived through.
    val releases = remember(feed, AppVersion.VERSION) {
        feed.releases.filter { release ->
            release.version.isNotBlank() &&
                !AppVersions.isNewer(release.version, AppVersion.VERSION)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .plainClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .fillMaxHeight(0.92f)
                .heightIn(max = 640.dp)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                .plainClickable(onClick = {}),
            colors = CardDefaults.cardColors(containerColor = SettingsCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            ChangelogCardBody(
                releases = releases,
                status = status,
                update = update,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ChangelogCardBody(
    releases: List<ChangelogRelease>,
    status: ChangelogStatus,
    update: UpdateState,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        DialogHeader(onDismiss)

        Spacer(Modifier.height(14.dp))
        ProductHeader()

        Spacer(Modifier.height(16.dp))
        UpdateBanner(update)

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SettingsBorder.copy(alpha = 0.7f))
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            if (releases.isEmpty()) {
                item { EmptyChangelog(status) }
            } else {
                items(releases, key = { it.version }) { release ->
                    ReleaseBlock(release)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.about_copyright),
                style = AppTypography.labelSmall,
                color = TextMuted,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsLinkButton(stringResource(Res.string.view_source)) {
                    ProjectLinks.open(ProjectLinks.REPOSITORY)
                }
                SettingsLinkButton(stringResource(Res.string.support_kofi)) {
                    ProjectLinks.open(ProjectLinks.KOFI)
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.changelog_header),
            style = AppTypography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = TextPrimary
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.close),
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ProductHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(Res.drawable.quicknotch_icon),
            contentDescription = stringResource(Res.string.app_name),
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(13.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.about_product_name),
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentSolid.copy(alpha = 0.14f))
                        .border(1.dp, AccentSolid.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.about_version, AppVersion.VERSION),
                        style = AppTypography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentSolid,
                        fontSize = 10.5.sp
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(Res.string.about_description),
                style = AppTypography.bodySmall,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * One state machine, one strip: checking is a quiet line, an available release
 * gets the CTA, a download gets a bar, a ready download gets "restart".
 */
@Composable
private fun UpdateBanner(state: UpdateState) {
    when (state) {
        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> {
            if (state == UpdateState.UpToDate) {
                Text(
                    text = stringResource(Res.string.changelog_up_to_date),
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is UpdateState.Available -> BannerCard(
            title = stringResource(Res.string.changelog_update_available, state.version),
            body = when {
                state.asset == null -> stringResource(Res.string.changelog_no_asset)
                !AppInstaller.canSelfUpdate -> stringResource(Res.string.changelog_update_manual)
                else -> stringResource(Res.string.changelog_update_hint)
            },
            action = when {
                state.asset != null && AppInstaller.canSelfUpdate ->
                    stringResource(Res.string.changelog_update_now) to { AppUpdates.download() }
                else -> stringResource(Res.string.changelog_view_release) to { AppUpdates.openReleasePage() }
            }
        )

        is UpdateState.Downloading -> BannerCard(
            title = stringResource(Res.string.changelog_downloading, state.version),
            body = "${(state.progress * 100).toInt()}%",
            progress = state.progress
        )

        is UpdateState.Ready -> BannerCard(
            title = stringResource(Res.string.changelog_update_ready, state.version),
            body = stringResource(Res.string.changelog_update_hint),
            action = stringResource(Res.string.changelog_restart_update) to { AppUpdates.install() }
        )

        is UpdateState.Installing -> BannerCard(
            title = stringResource(Res.string.changelog_updating, state.version),
            body = stringResource(Res.string.changelog_update_hint),
            progress = 0.98f
        )

        is UpdateState.Failed -> BannerCard(
            title = stringResource(Res.string.changelog_update_failed, state.message),
            body = stringResource(Res.string.changelog_view_release),
            action = stringResource(Res.string.changelog_retry) to { AppUpdates.check(force = true) }
        )
    }
}

@Composable
private fun BannerCard(
    title: String,
    body: String,
    progress: Float? = null,
    action: Pair<String, () -> Unit>? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentSolid.copy(alpha = 0.1f))
            .border(1.dp, AccentSolid.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AppTypography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            action?.let { (label, onClick) ->
                Spacer(Modifier.width(10.dp))
                PrimaryButton(text = label, onClick = onClick)
            }
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = AppTypography.bodySmall, color = TextSecondary, fontSize = 11.sp)
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AccentSolid,
                trackColor = SettingsElevated
            )
        }
    }
}

@Composable
private fun ReleaseBlock(release: ChangelogRelease) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SettingsElevated)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "v${release.version}",
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    fontSize = 10.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDate(release.date),
                style = AppTypography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (release.title.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = release.title,
                style = AppTypography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        val lines = if (release.changes.isNotEmpty()) {
            release.changes
        } else {
            release.highlights.map { ChangelogChange("improved", it) }
        }
        Spacer(Modifier.height(6.dp))
        lines.forEach { change ->
            ChangeRow(change)
        }
    }
}

@Composable
private fun ChangeRow(change: ChangelogChange) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(KindColors[change.kind.lowercase()] ?: AccentSolid)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = change.text,
            style = AppTypography.bodySmall,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmptyChangelog(status: ChangelogStatus) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val text = when (status) {
            ChangelogStatus.Loading -> stringResource(Res.string.changelog_loading)
            ChangelogStatus.Offline -> stringResource(Res.string.changelog_error)
            else -> stringResource(Res.string.changelog_empty)
        }
        Text(
            text = text,
            style = AppTypography.bodySmall,
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        if (status == ChangelogStatus.Offline) {
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(Res.string.changelog_retry), onClick = { AppUpdates.check(force = true) })
        }
    }
}

private val KindColors = mapOf(
    "new" to AccentGreen,
    "fixed" to AccentRed,
    "improved" to AccentSolid
)

private fun formatDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(value)
