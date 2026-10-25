package com.starkdroid.quicknotch.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the operating system's own process inspector: Task Manager on Windows,
 * Activity Monitor on macOS.
 *
 * The System page is a glance, not a debugger — the moment a ring looks wrong
 * the question is "which process is that?", and the OS answers it better than a
 * 34dp tile can. The notch stays a shortcut to the real tool rather than
 * growing a task list of its own.
 *
 * Launched detached on the IO dispatcher and fail-soft: a machine where the
 * inspector lives somewhere unusual (or is blocked by policy) simply does
 * nothing, exactly like a failed app launch. `open -a` uses the bundle name, so
 * a localized Activity Monitor in a non-English macOS still resolves.
 */
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
