package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/** Launches pinned applications; Windows uses cmd/UWP shell, macOS uses `open`. */
object AppLauncher {

    suspend fun launch(app: AppItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val execPath = app.executablePath.trim()

            if (execPath.startsWith("http://", ignoreCase = true) ||
                execPath.startsWith("https://", ignoreCase = true)
            ) {
                browse(execPath)
                return@runCatching
            }

            if (HostPlatform.isMac) {
                launchOnMac(app, execPath)
                return@runCatching
            }

            launchOnWindows(app, execPath)
        }
    }

    private fun launchOnMac(app: AppItem, execPath: String) {
        val file = File(execPath)
        val command = mutableListOf("open")
        if (file.extension.equals("app", ignoreCase = true) || file.isDirectory) {
            command += execPath
        } else if (!execPath.contains("/") && !file.exists()) {
            command += listOf("-a", execPath)
        } else {
            command += execPath
        }
        if (app.arguments.isNotBlank()) {
            command += "--args"
            command += app.arguments.split(" ").filter { it.isNotBlank() }
        }
        val builder = ProcessBuilder(command)
        val workingDir = app.workingDirectory?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
        if (workingDir != null) {
            builder.directory(workingDir)
        }
        builder.start()
    }

    private fun launchOnWindows(app: AppItem, execPath: String) {
        if (app.isUwp && !app.uwpAppUserModelId.isNullOrBlank()) {
            ProcessBuilder("cmd.exe", "/c", "start", "shell:AppsFolder\\${app.uwpAppUserModelId}").start()
            return
        }

        if (execPath.startsWith("shell:", ignoreCase = true)) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI(execPath))
            } else {
                ProcessBuilder("cmd.exe", "/c", "start", "", execPath).start()
            }
            return
        }

        if (!execPath.contains("\\") && !execPath.contains("/")) {
            val commandList = mutableListOf(execPath)
            if (app.arguments.isNotBlank()) {
                commandList.addAll(app.arguments.split(" ").filter { it.isNotBlank() })
            }
            ProcessBuilder(commandList).start()
            return
        }

        val file = File(execPath)
        val workingDir = app.workingDirectory?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
            ?: file.parentFile?.takeIf { it.exists() && it.isDirectory }

        val builder = ProcessBuilder()
        if (app.arguments.isNotBlank()) {
            val parts = mutableListOf(execPath)
            parts.addAll(app.arguments.split(" ").filter { it.isNotBlank() })
            builder.command(parts)
        } else {
            builder.command("cmd.exe", "/c", "start", "", "\"$execPath\"")
        }

        if (workingDir != null) {
            builder.directory(workingDir)
        }

        builder.start()
    }

    /** Brings a media provider forward by bundle id (macOS, e.g. `com.apple.Music`) or app name. */
    fun openMediaProvider(sourceId: String, sourceName: String) {
        runCatching {
            if (HostPlatform.isMac) {
                if (sourceId.isNotBlank()) {
                    ProcessBuilder("open", "-b", sourceId).start()
                } else if (sourceName.isNotBlank()) {
                    ProcessBuilder("open", "-a", sourceName).start()
                }
            } else if (HostPlatform.isWindows && sourceName.isNotBlank()) {
                ProcessBuilder("cmd.exe", "/c", "start", "", sourceName).start()
            }
        }
    }

    /**
     * Opens a URL in the browser. `open` goes first because [Desktop.browse] can return normally
     * without a browser having opened, and both outcomes are logged for the packaged app.
     */
    fun browse(url: String) {
        if (HostPlatform.isMac) {
            if (spawnAndReap("open", url)) return
            AppLog.line("open handed off nothing; trying AWT instead")
        }
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        if (uri != null && Desktop.isDesktopSupported() &&
            runCatching { Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) }.getOrDefault(false)
        ) {
            runCatching { Desktop.getDesktop().browse(uri) }
                .onSuccess { AppLog.line("opened a link in the browser via AWT") }
                .onFailure { AppLog.line("AWT browse failed too: ${it.message}") }
        } else {
            AppLog.line("no way to open a browser for a link")
        }
    }

    /** Spawns the hand-off and reaps it; `open` exits non-zero when it cannot pass the URL on. */
    private fun spawnAndReap(vararg command: String): Boolean = runCatching {
        val process = ProcessBuilder(*command).start()
        // Drain the child's pipes so a chatty one cannot fill them and wedge.
        process.inputStream.close()
        process.errorStream.close()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            AppLog.line("handing a link to the browser timed out (${command.first()})")
            return@runCatching false
        }
        val code = process.exitValue()
        AppLog.line("handed a link to the browser via ${command.first()} (exit $code)")
        code == 0
    }.getOrElse {
        AppLog.line("could not hand a link to the browser: ${it.message}")
        false
    }
}
