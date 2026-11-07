package com.starkdroid.quicknotch.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the OS process inspector (Task Manager, Activity Monitor); a blocked or missing one is silent. */
object SystemMonitor {

    suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (HostPlatform.isMac) {
                ProcessBuilder("open", "-a", "Activity Monitor").start()
            } else {
                ProcessBuilder("taskmgr.exe").start()
            }
        }.map { }
            .onFailure { error ->
            AppLog.line("system monitor launch failed: ${error.message}")
        }
    }
}
