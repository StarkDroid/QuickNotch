package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem

object InstalledAppsScanner {
    suspend fun scanAllInstalledApps(): List<AppItem> {
        return if (HostPlatform.isMac) {
            MacInstalledAppsScanner.scanAllInstalledApps()
        } else {
            WindowsInstalledAppsScanner.scanAllInstalledApps()
        }
    }
}
