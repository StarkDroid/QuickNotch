package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Launches pinned applications. Windows uses cmd/UWP shell; macOS uses `open`.
 */
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

    /**
     * Brings a media provider forward by launch identifier (bundle id on macOS,
     * e.g. `com.apple.Music`) or app name — used when the user clicks a now-playing
     * album art.
     */
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
     * Open a URL in the user's browser. Public because the calendar's OAuth
     * consent screen is just another link to hand to the OS — same code path,
     * so it also inherits the Windows `start`-via-cmd handling.
     */
    fun browse(url: String) {
        if (HostPlatform.isMac) {
            spawnAndReap("open", url)
            return
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(java.net.URI(url))
        } else {
            spawnAndReap("cmd.exe", "/c", "start", "", url)
        }
    }

    /**
     * Hand a URL to the OS and let the child die on its own, reaped either way.
     * The `waitFor` is not about the work — `open` returns as soon as the
     * browser is launched — it is so the child is never left as a zombie, and
     * so a shell that refuses to open anything is noticed instead of silently
     * leaving the user staring at a button that did nothing.
     */
    private fun spawnAndReap(vararg command: String) {
        runCatching {
            val process = ProcessBuilder(*command).start()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                System.err.println("QuickNotch: handing a link to the browser timed out: ${command.first()}")
            }
        }.onFailure {
            System.err.println("QuickNotch: could not hand a link to the browser: ${it.message}")
        }
    }
}
