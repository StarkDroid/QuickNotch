package com.starkdroid.quicknotch.media

import com.starkdroid.quicknotch.datasource.HostPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Unread notification count for the quickview's notification indicator — the
 * same number the macOS Dock shows as red badges, fed into a bell glyph + badge
 * bubble at the far right of the pill.
 *
 * macOS keeps its Notification Center store (the delivered-but-unread
 * notifications) in a sqlite database at
 * `~/Library/Group Containers/group.com.apple.usernoted/db2/db`; we count the
 * delivered `record` rows through `sqlite3`. That path is guarded by Full Disk
 * Access, so until a user grants it (or TCC otherwise permits the read) the
 * query fails and the count stays null — the indicator hides, exactly like any
 * other unreadable probe. A transient failure keeps the previous count rather
 * than flashing the indicator away. Windows has no equivalent unread store
 * yet, so the poller stays silent there.
 */
internal object NotificationCount {

    private const val PollIntervalMs = 3_000L
    private const val ProcTimeoutMs = 3_000L

    private val _count = MutableStateFlow<Int?>(null)
    /** Latest unread notification count; null while the OS store is unreadable. */
    val count: StateFlow<Int?> = _count.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start() {
        stop()
        if (!HostPlatform.isMac) return
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        job = active.launch {
            while (isActive) {
                query()?.let { _count.value = it }
                delay(PollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    private fun query(): Int? = runCatching {
        val db = System.getProperty("user.home") +
            "/Library/Group Containers/group.com.apple.usernoted/db2/db"
        val process = ProcessBuilder(
            "/usr/bin/sqlite3", "-noheader", "-batch", db,
            "select count(*) from record where delivered = 1;"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
        process.destroyForcibly()
        output.toIntOrNull()
    }.getOrNull()
}