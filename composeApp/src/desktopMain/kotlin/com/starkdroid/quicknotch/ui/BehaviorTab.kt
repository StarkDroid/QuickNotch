package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.StartupManager
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.theme.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BehaviorTab(
    config: NotchConfig,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BehaviorSwitchRow(
                        icon = Icons.Default.PowerSettingsNew,
                        title = stringResource(
                            if (HostPlatform.isMac) Res.string.autostart_title_mac
                            else Res.string.autostart_title_windows
                        ),
                        subtitle = stringResource(
                            if (HostPlatform.isMac) Res.string.autostart_desc_mac
                            else Res.string.autostart_desc_windows
                        ),
                        checked = config.autoStartOnBoot,
                        onCheckedChange = { checked ->
                            StartupManager.setAutoStartEnabled(checked)
                            onUpdate { it.copy(autoStartOnBoot = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        icon = Icons.Default.PushPin,
                        title = stringResource(Res.string.always_on_top_title),
                        subtitle = stringResource(Res.string.always_on_top_desc),
                        checked = config.alwaysOnTop,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(alwaysOnTop = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        icon = Icons.Default.Minimize,
                        title = stringResource(Res.string.collapse_on_launch_title),
                        subtitle = stringResource(Res.string.collapse_on_launch_desc),
                        checked = config.hideOnAppLaunch,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(hideOnAppLaunch = checked) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun BehaviorSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlatSurfaceElevated)
                    .border(1.dp, FlatBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentIndigoLight,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = AppTypography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        MinimalistRectSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}