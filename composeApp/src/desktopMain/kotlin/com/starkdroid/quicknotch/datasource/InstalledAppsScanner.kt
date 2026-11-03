package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object InstalledAppsScanner {
    // The installed list only changes when apps are added/removed, and the
    // underlying scan can be slow on Windows (registry + shell calls). Cache it
    // in memory so opening Settings again is instant and never re-runs the
    // blocking pass on the UI thread.
    private var cached: List<AppItem>? = null
    private val scanMutex = Mutex()

    /** True until the first (uncached) scan completes; lets UI skip the spinner. */
    val isScanPending: Boolean get() = cached == null

    suspend fun scanAllInstalledApps(): List<AppItem> {
        cached?.let { return it }
        return scanMutex.withLock {
            cached ?: run {
                val result = if (HostPlatform.isMac) {
                    MacInstalledAppsScanner.scanAllInstalledApps()
                } else {
                    WindowsInstalledAppsScanner.scanAllInstalledApps()
                }
                cached = result
                result
            }
        }
    }
}
