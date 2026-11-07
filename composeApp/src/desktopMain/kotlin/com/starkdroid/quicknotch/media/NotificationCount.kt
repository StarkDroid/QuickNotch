package com.starkdroid.quicknotch.media

import com.starkdroid.quicknotch.datasource.HostPlatform
import java.io.File
import java.util.concurrent.TimeUnit
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

/**
 * Counts unread notifications from usernoted's record table. Full Disk
 * Access is required; without it, or on Windows, the count is null and
 * the bell hides.
 */
internal object NotificationCount {

    private const val WatchTickMs = 250L
    private const val BackstopTicks = 12L // every 12 ticks = 3s, in case a store change slips the stat watch
    private const val ProcTimeoutMs = 3_000L

    private val _count = MutableStateFlow<Int?>(null)
    /** Latest notification count from the OS store; null while unreadable. */
    val count: StateFlow<Int?> = _count.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start() {
        stop()
        if (!HostPlatform.isMac) {
            // Windows has no OS unread store and no test flow posts local
            // counts anymore — stay null so the bell never shows a dead zero.
            return
        }
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        job = active.launch {
            // Fingerprint db, db-wal and db-shm every 250 ms; query only when one
            // changes. A 3 s backstop catches anything missed.
            var lastSignature: Long? = null
            var ticks = 0L
            while (isActive) {
                val signature = storeSignature()
                if (signature != lastSignature) {
                    lastSignature = signature
                    query()?.let { _count.value = it }
                } else if (ticks % BackstopTicks == BackstopTicks - 1L) {
                    query()?.let { _count.value = it }
                }
                ticks += 1
                delay(WatchTickMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    /**
     * Re-reads the store at once so a Full Disk Access grant is picked up
     * without waiting for the poll.
     */
    fun poke() {
        if (!HostPlatform.isMac) return
        // No new scope here: NotificationCount.start/stop own the only one, and
        // recreating it after stop() would leak an uncancelled coroutine.
        val active = scope ?: return
        if (active.isActive) active.launch { query()?.let { _count.value = it } }
    }

    /**
     * Deep-links the Full Disk Access pane; no-op off macOS.
     */
    fun openPermissionSettings() {
        if (HostPlatform.isMac) {
            runCatching {
                ProcessBuilder(
                    "open", "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles"
                ).start().waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    /**
     * (mtime, size, fileKey) across db, db-wal and db-shm. Null when the
     * store is unreadable, so callers keep the last count.
     */
    private fun storeSignature(): Long? {
        val dir = System.getProperty("user.home") +
            "/Library/Group Containers/group.com.apple.usernoted/db2"
        var h = 0L
        for (name in listOf("db", "db-wal", "db-shm")) {
            val file = try { File(dir, name) } catch (_: Throwable) { return null }
            val mtime = try { java.nio.file.Files.getLastModifiedTime(file.toPath()).toMillis() } catch (_: Throwable) { return null }
            val size = try { file.length() } catch (_: Throwable) { return null }
            h = h * 31 + mtime xor size
        }
        return h
    }

    private fun query(): Int? = runCatching {
        val db = System.getProperty("user.home") +
            "/Library/Group Containers/group.com.apple.usernoted/db2/db"
        // `record` is the ledger behind the Notification Center: one row per
        // delivered notification the OS still holds. Count(*) is the real-time
        // add/remove total — a new notification inserts, a dismissal/Open/Clear
        // deletes. (A per-app `app.badge` sum was evaluated and rejected: badges
        // don't move on dismissal, so it couldn't reflect removals.)
        val process = ProcessBuilder(
            "/usr/bin/sqlite3", "-noheader", "-batch", db,
            "select count(*) from record;"
        ).redirectErrorStream(true).start()
        if (!process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
            return@runCatching null
        }
        process.inputStream.bufferedReader().readText().trim().toIntOrNull()
    }.getOrNull()
}
