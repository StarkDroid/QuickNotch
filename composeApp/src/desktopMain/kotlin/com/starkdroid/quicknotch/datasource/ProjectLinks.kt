package com.starkdroid.quicknotch.datasource

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The project's public addresses, in one place so the tray, the changelog and
 * the update banner cannot drift apart.
 */
object ProjectLinks {

    const val REPOSITORY = "https://github.com/StarkDroid/QuickNotch"
    const val RELEASES = "$REPOSITORY/releases/latest"
    const val KOFI = "https://ko-fi.com/trishiraj"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Hands [url] to the OS browser through [AppLauncher.browse], the same path
     * the calendar consent screen uses. That call waits on the child process, so
     * it runs off the render thread; `Desktop.browse` was the tidier-looking
     * call but on macOS it returns without a browser having opened, which is
     * exactly how a working button reads as a dead one.
     */
    fun open(url: String) {
        scope.launch { AppLauncher.browse(url) }
    }
}
