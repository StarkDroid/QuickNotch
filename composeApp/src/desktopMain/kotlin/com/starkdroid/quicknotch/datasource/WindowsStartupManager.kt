package com.starkdroid.quicknotch.datasource

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

object WindowsStartupManager {

    private val REGISTRY_RUN_KEY = listOf("Software", "Microsoft", "Windows", "CurrentVersion", "Run").joinToString(File.separator)
    private const val APP_KEY_NAME = "QuickNotch"

    fun isAutoStartEnabled(): Boolean {
        return try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_RUN_KEY, APP_KEY_NAME)
        } catch (_: Exception) {
            false
        }
    }

    fun setAutoStartEnabled(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                val exePath = getAppExecutablePath()
                // The marker lets the app tell a boot auto-start (stay quietly
                // in the tray) from a manual launch (open the Settings window).
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_RUN_KEY,
                    APP_KEY_NAME,
                    "\"" + exePath + "\" " + StartupManager.LAUNCHED_AT_LOGIN_ARG
                )
            } else {
                if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_RUN_KEY, APP_KEY_NAME)) {
                    Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, REGISTRY_RUN_KEY, APP_KEY_NAME)
                }
            }
            true
        } catch (e: Exception) {
            System.err.println("QuickNotch: Failed to update auto-start registry: " + e.message)
            false
        }
    }

    private fun getAppExecutablePath(): String {
        return try {
            val codeSource = WindowsStartupManager::class.java.protectionDomain.codeSource
            val location = codeSource.location.toURI().path
            val file = File(location)
            if (file.extension.equals("exe", ignoreCase = true)) {
                file.absolutePath
            } else {
                val javaBin = System.getProperty("java.home") + "/bin/javaw.exe"
                "\"" + javaBin + "\" -jar \"" + file.absolutePath + "\""
            }
        } catch (_: Exception) {
            "QuickNotch.exe"
        }
    }
}
