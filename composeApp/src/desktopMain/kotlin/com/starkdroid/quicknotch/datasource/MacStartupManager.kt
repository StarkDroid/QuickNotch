package com.starkdroid.quicknotch.datasource

import java.io.File
import java.util.concurrent.TimeUnit

object MacStartupManager {
    private const val LABEL = "com.starkdroid.quicknotch"
    private val plistFile: File
        get() = File(
            System.getProperty("user.home"),
            "Library/LaunchAgents/$LABEL.plist"
        )

    fun setAutoStartEnabled(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                val args = currentProcessArguments()
                if (args.isEmpty()) {
                    System.err.println("QuickNotch: Could not resolve launch command for login item.")
                    return false
                }
                plistFile.parentFile?.mkdirs()
                plistFile.writeText(buildPlist(args))
                launchctl(listOf("bootout", "gui/${userId()}", LABEL))
                val loaded = launchctl(listOf("bootstrap", "gui/${userId()}", plistFile.absolutePath))
                if (!loaded) {
                    launchctl(listOf("load", plistFile.absolutePath))
                }
            } else {
                launchctl(listOf("bootout", "gui/${userId()}", LABEL))
                launchctl(listOf("unload", plistFile.absolutePath))
                if (plistFile.exists()) {
                    plistFile.delete()
                }
            }
            true
        } catch (e: Exception) {
            System.err.println("QuickNotch: Failed to update macOS login item: ${e.message}")
            false
        }
    }

    private fun currentProcessArguments(): List<String> {
        val info = ProcessHandle.current().info()
        val command = info.command().orElse(null) ?: return emptyList()
        val args = info.arguments().orElse(emptyArray()).toList()
        val packagedApp = findEnclosingAppBundle(File(command))
        // The marker keeps a boot auto-start tray-quiet while a manual launch opens Settings.
        return if (packagedApp != null) {
            listOf("/usr/bin/open", "-a", packagedApp.absolutePath, "--args", StartupManager.LAUNCHED_AT_LOGIN_ARG)
        } else {
            listOf(command) + args + StartupManager.LAUNCHED_AT_LOGIN_ARG
        }
    }

    private fun findEnclosingAppBundle(file: File): File? {
        var current: File? = file.absoluteFile
        while (current != null) {
            if (current.isDirectory && current.extension.equals("app", ignoreCase = true)) {
                return current
            }
            current = current.parentFile
        }
        return null
    }

    private fun buildPlist(args: List<String>): String {
        val argumentsXml = args.joinToString("\n") { arg ->
            "        <string>${escapeXml(arg)}</string>"
        }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$LABEL</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |$argumentsXml
            |    </array>
            |    <key>RunAtLoad</key>
            |    <true/>
            |</dict>
            |</plist>
            |
        """.trimMargin()
    }

    private fun launchctl(args: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(listOf("launchctl") + args)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun userId(): String = try {
        val process = ProcessBuilder("id", "-u").start()
        process.inputStream.bufferedReader().readText().trim().ifBlank { "501" }
    } catch (_: Exception) {
        "501"
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
