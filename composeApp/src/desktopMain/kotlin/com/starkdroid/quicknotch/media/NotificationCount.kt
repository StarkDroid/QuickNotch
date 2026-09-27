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
 * macOS keeps its Notification Center store (the delivered-but-uncleared
 * notifications) in a sqlite database at
 * `~/Library/Group Containers/group.com.apple.usernoted/db2/db`. The store
 * holds one row per notification still present in Notification Center and
 * *prunes* rows as they are dismissed (record ids get reused), so the plain
 * row count is the badge-like total. That path is guarded by Full Disk Access,
 * so until a user grants it the query fails and the count stays null — the
 * indicator hides exactly like any other unreadable probe, and settings offers
 * a "Grant Full Disk Access" button that deep-links into the Privacy pane. A
 * transient failure keeps the previous count rather than flashing the indicator
 * away.
 *
 * Windows exposes no public unread store, so the count there is a session-local
 * tally of the notifications QuickNotch itself posts (the test button), which
 * keeps the badge demo identical on both platforms; the OS toast itself is a
 * best-effort WinRT post that needs the installed Start Menu shortcut/AUMID.
 */
internal object NotificationCount {

    private const val PollIntervalMs = 3_000L
    private const val ProcTimeoutMs = 3_000L

    private val _count = MutableStateFlow<Int?>(null)
    /** Latest unread notification count; null while the OS store is unreadable. */
    val count: StateFlow<Int?> = _count.asStateFlow()

    @Volatile
    private var toastScript: File? = null
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

    private fun query(): Int? = runCatching {
        val db = System.getProperty("user.home") +
            "/Library/Group Containers/group.com.apple.usernoted/db2/db"
        // Count the store's rows: one per notification still present in
        // Notification Center, pruned on dismissal. Do NOT filter on a
        // "delivered" boolean — older macOS schemas had one, current ones do
        // not (only `delivered_date` / `presented`, and `presented` reads 0
        // even for a just-delivered banner), so the old `where delivered = 1`
        // always errored and the indicator acted as if FDA were never granted.
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