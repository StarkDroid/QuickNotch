package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/**
 * Handles launching Windows executables, shortcuts (.lnk), UWP applications,
 * and command-line utilities safely.
 */
object AppLauncher {

    suspend fun launch(app: AppItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val execPath = app.executablePath.trim()

            // 1. UWP / Shell App UserModelId
            if (app.isUwp && !app.uwpAppUserModelId.isNullOrBlank()) {
                ProcessBuilder("cmd.exe", "/c", "start", "shell:AppsFolder\${app.uwpAppUserModelId}").start()
                return@runCatching
            }

            // 2. Shell URI or Web URL
            if (execPath.startsWith("http://", ignoreCase = true) ||
                execPath.startsWith("https://", ignoreCase = true) ||
                execPath.startsWith("shell:", ignoreCase = true)
            ) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(java.net.URI(execPath))
                } else {
                    ProcessBuilder("cmd.exe", "/c", "start", "", execPath).start()
                }
                return@runCatching
            }

            // 3. System command (e.g. wt.exe, calc.exe, notepad.exe)
            if (!execPath.contains("\\") && !execPath.contains("/")) {
                val commandList = mutableListOf(execPath)
                if (app.arguments.isNotBlank()) {
                    commandList.addAll(app.arguments.split(" ").filter { it.isNotBlank() })
                }
                ProcessBuilder(commandList).start()
                return@runCatching
            }

            // 4. Standard File / Shortcut
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
    }
}
