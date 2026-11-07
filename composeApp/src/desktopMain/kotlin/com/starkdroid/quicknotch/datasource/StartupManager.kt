package com.starkdroid.quicknotch.datasource

object StartupManager {
    /** Auto-start marker: a login launch stays tray-quiet, a manual launch opens Settings. */
    const val LAUNCHED_AT_LOGIN_ARG = "--quicknotch-launched-at-login"

    fun setAutoStartEnabled(enabled: Boolean): Boolean {
        return if (HostPlatform.isMac) {
            MacStartupManager.setAutoStartEnabled(enabled)
        } else {
            WindowsStartupManager.setAutoStartEnabled(enabled)
        }
    }
}
