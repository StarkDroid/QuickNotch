package com.starkdroid.quicknotch.datasource

import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MacInstalledAppsScanner {
    private val IGNORED_TERMS = listOf(
        "uninstall", "uninstaller", "remove", "help", "documentation", "readme",
        "license", "website", "changelog", "configure", "manual", "install"
    )

    suspend fun scanAllInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, AppItem>()
        val roots = listOf(
            File("/Applications"),
            File("/System/Applications"),
            File("/System/Applications/Utilities"),
            File(System.getProperty("user.home"), "Applications")
        )
        roots.forEach { collectAppBundles(it, maxDepth = 2, map = results) }
        results.values
            .filter { item ->
                item.name.isNotBlank() &&
                    IGNORED_TERMS.none { item.name.contains(it, ignoreCase = true) }
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun collectAppBundles(root: File, maxDepth: Int, map: MutableMap<String, AppItem>) {
        if (!root.exists() || !root.isDirectory) return
        visit(root, 0, maxDepth, map)
    }

    private fun visit(dir: File, depth: Int, maxDepth: Int, map: MutableMap<String, AppItem>) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.isDirectory && file.extension.equals("app", ignoreCase = true)) {
                addBundle(file, map)
            } else if (file.isDirectory && !file.name.startsWith(".") && !file.name.endsWith(".app")) {
                visit(file, depth + 1, maxDepth, map)
            }
        }
    }

    private fun addBundle(bundle: File, map: MutableMap<String, AppItem>) {
        // Read the plist once and share it across the DisplayName / Name / IconFile / Identifier lookups.
        val plistText = readPlistText(bundle)
        val displayName = plistString(plistText, "CFBundleDisplayName")
            ?: plistString(plistText, "CFBundleName")
            ?: bundle.nameWithoutExtension
        val key = displayName.trim().lowercase()
        if (key.isBlank() || map.containsKey(key)) return
        if (IGNORED_TERMS.any { displayName.contains(it, ignoreCase = true) }) return

        val iconFile = findBundleIcns(bundle, plistText)
        val bundleId = plistString(plistText, "CFBundleIdentifier") ?: bundle.nameWithoutExtension
        map[key] = AppItem(
            id = "mac_" + bundleId.lowercase().replace(Regex("[^a-z0-9._-]+"), "_"),
            name = displayName.trim(),
            executablePath = bundle.absolutePath,
            iconPath = iconFile?.absolutePath ?: bundle.absolutePath
        )
    }

    private fun findBundleIcns(bundle: File, plistText: String?): File? {
        val resources = File(bundle, "Contents/Resources")
        val iconFileName = plistString(plistText, "CFBundleIconFile")
        if (!iconFileName.isNullOrBlank()) {
            val named = if (iconFileName.endsWith(".icns", ignoreCase = true)) iconFileName else "$iconFileName.icns"
            val candidate = File(resources, named)
            if (candidate.isFile) return candidate
        }
        return resources.takeIf { it.isDirectory }
            ?.listFiles { file -> file.isFile && file.extension.equals("icns", ignoreCase = true) }
            ?.maxByOrNull { it.length() }
    }

    fun readBundleInfo(bundle: File, key: String): String? = plistString(readPlistText(bundle), key)

    private fun readPlistText(bundle: File): String? {
        val plist = File(bundle, "Contents/Info.plist")
        if (!plist.isFile) return null
        return try {
            val bytes = plist.readBytes()
            val header = bytes.decodeToString(0, minOf(bytes.size, 64))
            val isXml = header.contains("<?xml") || header.contains("<plist") || header.contains("plist")
            if (!isXml) return null
            bytes.decodeToString()
        } catch (_: Exception) {
            null
        }
    }

    private fun plistString(plistText: String?, key: String): String? {
        if (plistText == null) return null
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<string>([^<]+)</string>", RegexOption.IGNORE_CASE)
        return regex.find(plistText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
