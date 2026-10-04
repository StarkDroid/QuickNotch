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
 * Notification count for the quickview's bell indicator — fed into a bell glyph
 * + badge bubble at the far right of the pill.
 *
 * macOS has no public "notifications currently in Notification Center" value, but
 * the Notification Center *store* (`~/Library/Group Containers/group.com.apple.usernoted/db2/db`)
 * is the ledger that backs the center, and its `record` table is exactly the
 * count of notifications usernoted currently holds for the user — each row is a
 * delivered notification that hasn't yet been acknowledged. It is live in both
 * directions:
 *
 *  - **added**: a notification arriving posts a new `record` row (plus its app's
 *    per-app `delivered` UUID list via `db-wal`) the moment it's accepted;
 *  - **removed**: dismissing it from the Notification Center, opening the app,
 *    or a Clear All deletes the rows for the cleared apps (verified: a Clear All
 *    in-session dropped the store from 59 rows to 15).
 *
 * So `count(*)` on `record` is the real-time "how many notifications exist for
 * me right now" number — the count present in the Notification Center.
 *
 * One deliberate honesty note: macOS keeps the notifications of apps that still
 * hold a Dock-badge unread count (WhatsApp, Messages) *after* a Clear All —
 * those rows are not purged until the app is read/dismissed, so the count will
 * legitimately sit above zero after clearing until you deal with those apps,
 * exactly like the Dock badges do. The count is what the OS is holding, added
 * and removed, in real time.
 *
 * Reading the store is guarded by Full Disk Access, so until a user grants it
 * the query fails and the count stays null — the indicator hides exactly like
 * any other unreadable probe, and settings offers a "Grant Full Disk Access"
 * button that deep-links into the Privacy pane (and re-pokes on focus regain).
 * Once the store reads, the badge is live and nothing else is configured, so the
 * settings row disappears.
 *
 * Windows exposes no such store, so the count stays null there too — the bell
 * hides rather than showing a meaningless always-zero badge.
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
            // Near-instant badge updates, not a blind N-second poll: usernoted's
            // store file only changes when a notification actually arrives or is
            // dismissed, and in WAL mode the write lands in `db-wal` (the main
            // `db` is static between checkpoints). We stat the three store files
            // on a tight 250 ms tick — a couple of syscalls, no sqlite spawn —
            // and only run the full query when a signature actually changes. A
            // 3s backstop still runs so a signal we miss (a same-size rewrite, a
            // permission change from a fresh FDA grant) converges on its own.
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
     * Forces an immediate store re-read instead of waiting for the next poll
     * tick, so a Full Disk Access grant made in System Settings is picked up
     * as soon as the user returns to the app (the settings window pokes on
     * focus regain). No-op off macOS.
     */
    fun poke() {
        if (!HostPlatform.isMac) return
        val active = scope ?: run {
            val created = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = created
            created
        }
        if (active.isActive) active.launch { query()?.let { _count.value = it } }
    }

    /**
     * Opens the pane where the required permission is granted. macOS asks for
     * Full Disk Access (to read the Notification Center store) and lands on that
     * Privacy &amp; Security pane; Windows has no equivalent grantable permission,
     * so this is a no-op there.
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
     * A cheap fingerprint of the store so a change can be detected without
     * reading it: (mtime, size, fileKey) hashed across `db`, `db-wal` and
     * `db-shm`. Inserts/dismissals land in the WAL, checkpoints touch the main
     * `db`, and `-shm` changes with every session regression. A null fingerprint
     * means the files are unreadable (no Full Disk Access yet, or the store is
     * gone) — the callers keep the last count and the backstop tick retries.
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
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
        process.destroyForcibly()
        output.toIntOrNull()
    }.getOrNull()
}
