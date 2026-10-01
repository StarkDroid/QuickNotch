package com.starkdroid.quicknotch.datasource

object StartupManager {
    fun isAutoStartEnabled(): Boolean {
        return if (HostPlatform.isMac) {
            MacStartupManager.isAutoStartEnabled()
        } else {
            WindowsStartupManager.isAutoStartEnabled()
        }
    }

    fun setAutoStartEnabled(enabled: Boolean): Boolean {
        return if (HostPlatform.isMac) {
            MacStartupManager.setAutoStartEnabled(enabled)
        } else {
            WindowsStartupManager.setAutoStartEnabled(enabled)
        }
    }
}
