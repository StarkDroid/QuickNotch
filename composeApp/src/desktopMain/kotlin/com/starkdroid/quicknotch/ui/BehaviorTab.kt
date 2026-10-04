package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.StartupManager
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.theme.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BehaviorTab(
    repository: FavoritesRepository
) {
    // The tab owns its config subscription so toggling a switch recomposes
    // only this tab.
    val config by repository.config.collectAsState()
    val onUpdate: ((NotchConfig) -> NotchConfig) -> Unit = { transform ->
        repository.updateConfig(transform)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            TabEntrance(index = 0) {
                SettingsCard {
                    SectionHeader(
                        Icons.Default.PowerSettingsNew,
                        stringResource(Res.string.behavior_header),
                        stringResource(Res.string.settings_behavior_desc)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    BehaviorSwitchRow(
                        icon = Icons.Default.PowerSettingsNew,
                        title = stringResource(
                            if (HostPlatform.isMac) Res.string.autostart_title_mac
                            else Res.string.autostart_title_windows
                        ),
                        checked = config.autoStartOnBoot,
                        onCheckedChange = { checked ->
                            StartupManager.setAutoStartEnabled(checked)
                            onUpdate { it.copy(autoStartOnBoot = checked) }
                        }
                    )

                    HorizontalDivider(color = SettingsBorder.copy(alpha = 0.7f))

                    BehaviorSwitchRow(
                        icon = Icons.Default.PushPin,
                        title = stringResource(Res.string.always_on_top_title),
                        checked = config.alwaysOnTop,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(alwaysOnTop = checked) }
                        }
                    )

                    HorizontalDivider(color = SettingsBorder.copy(alpha = 0.7f))

                    BehaviorSwitchRow(
                        icon = Icons.Default.Minimize,
                        title = stringResource(Res.string.collapse_on_launch_title),
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
