package com.starkdroid.quicknotch.datasource

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The project's public addresses, in one place so the tray, changelog and banner cannot drift. */
object ProjectLinks {

    const val REPOSITORY = "https://github.com/StarkDroid/QuickNotch"
    const val RELEASES = "$REPOSITORY/releases/latest"
    const val KOFI = "https://ko-fi.com/trishiraj"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Opens [url] via [AppLauncher.browse]: `Desktop.browse` returns on macOS without opening a browser. */
    fun open(url: String) {
        scope.launch { AppLauncher.browse(url) }
    }
}
