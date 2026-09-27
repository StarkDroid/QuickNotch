package com.starkdroid.quicknotch.datasource

object StartupManager {
    /**
     * Command-line marker appended to the auto-start launch commands (the macOS
     * LaunchAgent and the Windows Run-key value). `main` checks for it so a
     * login/boot auto-start stays quietly behind the tray, while a fresh manual
     * launch (no marker) opens the Settings window.
     */
    const val LAUNCHED_AT_LOGIN_ARG = "--quicknotch-launched-at-login"

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
