package com.starkdroid.quicknotch.datasource

object HostPlatform {
    private val osName: String = System.getProperty("os.name").orEmpty()

    val isMac: Boolean =
        osName.contains("mac", ignoreCase = true) || osName.contains("darwin", ignoreCase = true)

    val isWindows: Boolean = osName.contains("win", ignoreCase = true)
}
