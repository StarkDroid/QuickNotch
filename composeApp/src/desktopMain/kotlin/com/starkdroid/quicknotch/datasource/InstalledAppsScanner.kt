package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object InstalledAppsScanner {
    private val IGNORED_TERMS = listOf(
        "uninstall", "unins000", "remove", "help", "documentation", "readme",
        "license", "website", "changelog", "configure", "manual", "install"
    )

    suspend fun scanAllInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AppItem>()
        scanSystemTools(results)
        scanStartMenuDirectories(results)
        scanDesktopDirectories(results)
        scanRegistryUninstallEntries(results)
        results.values
            .filter { item ->
                item.name.isNotBlank() &&
                        IGNORED_TERMS.none { item.name.contains(it, ignoreCase = true) } &&
                        IGNORED_TERMS.none { item.executablePath.contains(it, ignoreCase = true) }
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun scanSystemTools(map: MutableMap<String, AppItem>) {
        val winDir = System.getenv("WINDIR") ?: "C:/Windows"
        val system32 = winDir + "/System32"
        val tools = listOf(
            Triple("File Explorer", winDir + "/explorer.exe", winDir + "/explorer.exe"),
            Triple("Windows Terminal", "wt.exe", system32 + "/cmd.exe"),
            Triple("Command Prompt", system32 + "/cmd.exe", system32 + "/cmd.exe"),
            Triple("PowerShell", system32 + "/WindowsPowerShell/v1.0/powershell.exe", system32 + "/WindowsPowerShell/v1.0/powershell.exe"),
            Triple("Task Manager", system32 + "/Taskmgr.exe", system32 + "/Taskmgr.exe"),
            Triple("Notepad", winDir + "/notepad.exe", winDir + "/notepad.exe"),
            Triple("Calculator", system32 + "/calc.exe", system32 + "/calc.exe"),
            Triple("Paint", system32 + "/mspaint.exe", system32 + "/mspaint.exe"),
            Triple("Snipping Tool", system32 + "/SnippingTool.exe", system32 + "/SnippingTool.exe"),
            Triple("Control Panel", system32 + "/control.exe", system32 + "/control.exe")
        )
        for ((name, exe, icon) in tools) {
            val key = name.lowercase()
            if (exe == "wt.exe" || File(exe).exists()) {
                map[key] = AppItem(
                    id = "sys_" + name.lowercase().replace(" ", "_"),
                    name = name,
                    executablePath = exe,
                    iconPath = if (File(icon).exists()) icon else null
                )
            }
        }
    }
private fun scanStartMenuDirectories(map: MutableMap<String, AppItem>) {
        val appData = System.getenv("APPDATA")
        val progData = System.getenv("ProgramData")
        val dirs = listOfNotNull(
            appData?.let { File(it, "Microsoft/Windows/Start Menu/Programs") },
            progData?.let { File(it, "Microsoft/Windows/Start Menu/Programs") }
        )
        dirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                scanFolderForShortcuts(dir, map)
            }
        }
    }

    private fun scanDesktopDirectories(map: MutableMap<String, AppItem>) {
        val userProfile = System.getenv("USERPROFILE")
        val publicProfile = System.getenv("PUBLIC")
        val dirs = listOfNotNull(
            userProfile?.let { File(it, "Desktop") },
            publicProfile?.let { File(it, "Desktop") }
        )
        dirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                scanFolderForShortcuts(dir, map)
            }
        }
    }

    private fun scanFolderForShortcuts(folder: File, map: MutableMap<String, AppItem>) {
        try {
            folder.walkTopDown()
                .maxDepth(4)
                .filter { file -> file.isFile && (file.extension.equals("lnk", ignoreCase = true) || file.extension.equals("exe", ignoreCase = true)) }
                .forEach { file ->
                    val cleanName = file.nameWithoutExtension.trim()
                    val key = cleanName.lowercase()
                    if (cleanName.isNotBlank() && !map.containsKey(key)) {
                        if (IGNORED_TERMS.none { cleanName.contains(it, ignoreCase = true) }) {
                            map[key] = AppItem(
                                id = "sc_" + UUID.randomUUID().toString(),
                                name = cleanName,
                                executablePath = file.absolutePath,
                                iconPath = file.absolutePath
                            )
                        }
                    }
                }
        } catch (_: Exception) {}
    }

    private fun scanRegistryUninstallEntries(map: MutableMap<String, AppItem>) {
        val p1 = listOf("SOFTWARE", "Microsoft", "Windows", "CurrentVersion", "Uninstall").joinToString(File.separator)
        val p2 = listOf("SOFTWARE", "WOW6432Node", "Microsoft", "Windows", "CurrentVersion", "Uninstall").joinToString(File.separator)
        val keys = listOf(
            WinReg.HKEY_LOCAL_MACHINE to p1,
            WinReg.HKEY_LOCAL_MACHINE to p2,
            WinReg.HKEY_CURRENT_USER to p1
        )
        for ((hKey, path) in keys) {
            try {
                if (!Advapi32Util.registryKeyExists(hKey, path)) continue
                val subKeys = Advapi32Util.registryGetKeys(hKey, path) ?: continue
                for (subKey in subKeys) {
                    val entryPath = listOf(path, subKey).joinToString(File.separator)
                    try {
                        val displayName = Advapi32Util.registryGetStringValue(hKey, entryPath, "DisplayName")
                        if (displayName.isNullOrBlank()) continue
                        val key = displayName.trim().lowercase()
                        if (map.containsKey(key)) continue
                        if (Advapi32Util.registryValueExists(hKey, entryPath, "SystemComponent")) {
                            try {
                                if (Advapi32Util.registryGetIntValue(hKey, entryPath, "SystemComponent") == 1) continue
                            } catch (_: Exception) {}
                        }
                        val displayIcon = try {
                            Advapi32Util.registryGetStringValue(hKey, entryPath, "DisplayIcon")
                        } catch (_: Exception) { null }
                        val installLocation = try {
                            Advapi32Util.registryGetStringValue(hKey, entryPath, "InstallLocation")
                        } catch (_: Exception) { null }

                        var exePath: String? = null
                        if (!displayIcon.isNullOrBlank()) {
                            val cleanIcon = displayIcon.split(",")[0].trim("""" """.toCharArray()[0], """" """.toCharArray()[1])
                            if (cleanIcon.endsWith(".exe", ignoreCase = true) && File(cleanIcon).exists()) {
                                exePath = cleanIcon
                            }
                        }
                        if (exePath == null && !installLocation.isNullOrBlank()) {
                            val dir = File(installLocation.trim("""" """.toCharArray()[0], """" """.toCharArray()[1]))
                            if (dir.exists() && dir.isDirectory) {
                                val foundExe = dir.listFiles { f -> f.isFile && f.extension.equals("exe", ignoreCase = true) }
                                    ?.minByOrNull { it.name.length }
                                exePath = foundExe?.absolutePath
                            }
                        }
                        if (exePath != null) {
                            map[key] = AppItem(
                                id = "reg_" + UUID.randomUUID().toString(),
                                name = displayName.trim(),
                                executablePath = exePath,
                                iconPath = displayIcon ?: exePath
                            )
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
}
