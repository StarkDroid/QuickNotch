package com.starkdroid.quicknotch.datasource

import java.io.File

/** The per-user app-data directory, resolved here so every writer and reader agrees on it. */
object AppPaths {
    val configDir: File by lazy {
        val dir = when {
            HostPlatform.isMac -> File(
                System.getProperty("user.home"),
                "Library/Application Support/QuickNotch"
            )
            else -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) {
                    File(appData, "QuickNotch")
                } else {
                    File(System.getProperty("user.home"), ".quicknotch")
                }
            }
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }
}
