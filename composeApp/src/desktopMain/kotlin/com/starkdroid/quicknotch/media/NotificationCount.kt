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
 * Unread notification count for the quickview's notification indicator — fed
 * into a bell glyph + badge bubble at the far right of the pill.
 *
 * macOS has no public "notifications currently in Notification Center" value;
 * the closest real-time unread data it publishes is the per-app icon badge the
 * Notification Center store keeps (the same integers the Dock shows). The store
 * at `~/Library/Group Containers/group.com.apple.usernoted/db2/db` carries a
 * simple `app` table with a `badge` column per identifier, so the quickview
 * badge is the **sum of non-null `app.badge` values** — it rises when apps post
 * with a badge and falls when they clear one (opening the app, or dismissing
 * notifications for apps that don't manage their own badge), exactly mirroring
 * the Dock. That path is guarded by Full Disk Access, so until a user grants it
 * the query fails and the count stays null — the indicator hides exactly like
 * any other unreadable probe, and settings offers a "Grant Full Disk Access"
 * button that deep-links into the Privacy pane.
 *
 * The quickview indicator that this object was originally built around (the
 * `record` row count) turned out not to track the Notification Center list at
 * all: dismissed notifications are only pruned lazily and badge-worthy rows
 * (WhatsApp, Messages) persist after a Clear All, so the number never hit zero
 * and lied about removals. The `app.badge` sum is the OS's own unread totals.
 *
 * Windows exposes no public unread store, so the count there is a session-local
 * tally of the notifications QuickNotch itself posts (the test button), which
 * keeps the badge demo identical on both platforms; the OS toast itself is a
 * best-effort WinRT post that needs the installed Start Menu shortcut/AUMID.
 *
 * On macOS the "Send test notification" flow is made deterministic by setting
 * QuickNotch's *own* app badge (`badge_bridge.m` -> `setBadgeCount:`) per press
 * — osascript banners don't reliably insert into the store, so without that the
 * demo badge would sometimes not move.
 */
internal object NotificationCount {

    private const val WatchTickMs = 250L
    private const val BackstopTicks = 12L // every 12 ticks = 3s, in case a store change slips the stat watch
    private const val ProcTimeoutMs = 3_000L

    private val _count = MutableStateFlow<Int?>(null)
    /** Latest unread notification count; null while the OS store is unreadable. */
    val count: StateFlow<Int?> = _count.asStateFlow()

    @Volatile
    private var toastScript: File? = null
    /** In-process bridge that sets our own macOS app badge (null in dev runs). */
    @Volatile
    private var setBadgeFn: com.sun.jna.Function? = null
    /** Running total for the macOS test button (our own badge only). */
    @Volatile
    private var ownUnread = 0
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start() {
        stop()
        if (HostPlatform.isWindows) {
            // No OS store to read: the badge counts our own posts, starting at
            // a clean zero so the bell is visible (badge grows on each test).
            toastScript = NativeResources.extract("windows/toast.ps1", ".ps1")
            _count.value = 0
            return
        }
        if (!HostPlatform.isMac) return
        loadBadgeBridge()
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        job = active.launch {
            // Near-instant badge updates, not a blind N-second poll: usernoted's
            // store file only changes when a notification actually arrives or is
            // dismissed (a badge write lands in the database alongside the
            // record), and in WAL mode the write goes to `db-wal` (the main `db`
            // is static between checkpoints). We stat the three store files on a
            // tight 250 ms tick — a couple of syscalls, no sqlite spawn — and
            // only run the full query when a signature actually changes. A slower
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
        setBadgeFn = null
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
     * Opens the OS pane where the required permission is granted. macOS asks for
     * Full Disk Access (to read the Notification Center store) and lands on that
     * Privacy &amp; Security pane; Windows has no equivalent grantable permission,
     * so this is a no-op there — the test-notification button is offered directly.
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
     * Posts one test notification so users can watch the badge stack on the
     * quickview. macOS posts a real Notification Center banner through
     * `osascript` (no extra permission — the store poll then picks up the
     * increment like any delivered notification); Windows has no store, so the
     * session-local count advances directly and a best-effort WinRT toast is
     * attempted for the visual confirmation.
     */
    fun postTestNotification(title: String, body: String) {
        when {
            HostPlatform.isMac -> postMac(title, body)
            HostPlatform.isWindows -> {
                _count.value = (_count.value ?: 0) + 1
                postWindows(title, body)
            }
        }
    }

    private fun postMac(title: String, body: String) {
        // Make the test flow deterministic: osascript banners don't reliably
        // insert into the Notification Center store, so also bump QuickNotch's
        // OWN app badge via the compiled bridge — the quickview total is the
        // store's summed app badges, so the increment is guaranteed to show.
        ownUnread += 1
        val setBadge = setBadgeFn
        if (setBadge != null) {
            runCatching { setBadge.invoke(java.lang.Void::class.java, arrayOf(ownUnread)) }
        }
        val launch = scope ?: run {
            val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = active
            active
        }
        launch.launch {
            runCatching {
                val script = "display notification \"${jsEscape(body)}\" with title \"${jsEscape(title)}\""
                val process = ProcessBuilder("osascript", "-e", script).start()
                process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
                process.destroyForcibly()
            }
        }
    }

    /**
     * Loads the compiled `badge-bridge.dylib` (`quicknotch_set_badge_count`) so
     * the test button can move QuickNotch's own app badge. Missing bridge (a
     * non-macOS-built jar, or a dev `:run` without the packaged resource) keeps
     * it null and the button simply posts the banner — fail soft.
     */
    private fun loadBadgeBridge() {
        val lib = NativeResources.extract("macos/badge-bridge.dylib", ".dylib") ?: return
        setBadgeFn = runCatching {
            com.sun.jna.NativeLibrary.getInstance(lib.absolutePath)
                .getFunction("quicknotch_set_badge_count")
        }.getOrNull()
    }

    private fun postWindows(title: String, body: String) {
        val file = toastScript ?: return
        runCatching {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath,
                "-Title", title,
                "-Message", body
            ).redirectErrorStream(true).start()
            process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
            process.destroyForcibly()
        }
    }

    private fun jsEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

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
        // Sum the OS's per-app icon badges (the same numbers the Dock shows).
        // A `record`-row count was the original approach but it doesn't track
        // the Notification Center list: dismissed rows are only pruned lazily and
        // badge-worthy apps (WhatsApp, Messages) keep their rows after a Clear
        // All, so it never reflected removals. The `app.badge` column is the
        // store's real-time unread total — bumped by usernoted on arrival and
        // dropped when an app clears its badge.
        val process = ProcessBuilder(
            "/usr/bin/sqlite3", "-noheader", "-batch", db,
            "select coalesce(sum(badge), 0) from app where badge is not null;"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
        process.destroyForcibly()
        output.toIntOrNull()
    }.getOrNull()
}