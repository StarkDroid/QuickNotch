package com.starkdroid.quicknotch.datasource

import java.io.File

/**
 * The per-user app-data directory, in one place.
 *
 * `FavoritesRepository` used to compute this inline, which meant anything that
 * needs the directory *before* the repository exists — the render-backend
 * marker read at startup, for instance — had no way to reach it. Keeping the
 * resolution here also means the path can never drift between the settings
 * writer and the readers.
 */
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
