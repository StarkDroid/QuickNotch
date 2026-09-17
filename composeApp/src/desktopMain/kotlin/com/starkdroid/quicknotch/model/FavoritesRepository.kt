package com.starkdroid.quicknotch.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages persistent storage of QuickNotch settings and pinned applications.
 */
class FavoritesRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storageDir: File by lazy {
        val appData = System.getenv("APPDATA")
        val dir = if (!appData.isNullOrBlank()) {
            File(appData, "QuickNotch")
        } else {
            File(System.getProperty("user.home"), ".quicknotch")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val configFile: File by lazy { File(storageDir, "config.json") }
    private val favoritesFile: File by lazy { File(storageDir, "favorites.json") }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<NotchConfig> = _config.asStateFlow()

    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<AppItem>> = _favorites.asStateFlow()

    private fun loadConfig(): NotchConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                json.decodeFromString<NotchConfig>(content)
            } else {
                val defaultCfg = NotchConfig()
                saveConfigDirect(defaultCfg)
                defaultCfg
            }
        } catch (e: Exception) {
            System.err.println("QuickNotch: Failed to load config, using default: ${e.message}")
            NotchConfig()
        }
    }

    private fun loadFavorites(): List<AppItem> {
        return try {
            if (favoritesFile.exists()) {
                val content = favoritesFile.readText()
                val list = json.decodeFromString<List<AppItem>>(content)
                if (list.isNotEmpty()) list else getDefaultFavorites()
            } else {
                val defaults = getDefaultFavorites()
                saveFavoritesDirect(defaults)
                defaults
            }
        } catch (e: Exception) {
            System.err.println("QuickNotch: Failed to load favorites, using defaults: ${e.message}")
            getDefaultFavorites()
        }
    }

    private fun saveConfigDirect(cfg: NotchConfig) {
        try {
            configFile.writeText(json.encodeToString(cfg))
        } catch (e: Exception) {
            System.err.println("QuickNotch: Error saving config: ${e.message}")
        }
    }

    private fun saveFavoritesDirect(list: List<AppItem>) {
        try {
            favoritesFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            System.err.println("QuickNotch: Error saving favorites: ${e.message}")
        }
    }

    fun updateConfig(transform: (NotchConfig) -> NotchConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        scope.launch(Dispatchers.IO) {
            saveConfigDirect(updated)
        }
    }

    fun setConfig(newConfig: NotchConfig) {
        _config.value = newConfig
        scope.launch(Dispatchers.IO) {
            saveConfigDirect(newConfig)
        }
    }

    fun addFavorite(item: AppItem) {
        val current = _favorites.value.toMutableList()
        if (current.none { it.id == item.id || it.executablePath.equals(item.executablePath, ignoreCase = true) }) {
            current.add(item)
            _favorites.value = current
            scope.launch(Dispatchers.IO) {
                saveFavoritesDirect(current)
            }
        }
    }

    fun removeFavorite(id: String) {
        val current = _favorites.value.toMutableList()
        if (current.removeAll { it.id == id }) {
            _favorites.value = current
            scope.launch(Dispatchers.IO) {
                saveFavoritesDirect(current)
            }
        }
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val current = _favorites.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _favorites.value = current
            scope.launch(Dispatchers.IO) {
                saveFavoritesDirect(current)
            }
        }
    }

    fun setFavorites(list: List<AppItem>) {
        _favorites.value = list
        scope.launch(Dispatchers.IO) {
            saveFavoritesDirect(list)
        }
    }

    fun resetToDefaults() {
        val defaults = getDefaultFavorites()
        _favorites.value = defaults
        val defaultCfg = NotchConfig()
        _config.value = defaultCfg
        scope.launch(Dispatchers.IO) {
            saveFavoritesDirect(defaults)
            saveConfigDirect(defaultCfg)
        }
    }

    private fun getDefaultFavorites(): List<AppItem> {
        val list = mutableListOf<AppItem>()
        val winDir = System.getenv("WINDIR") ?: "C:/Windows"
        val progFiles = System.getenv("ProgramFiles") ?: "C:/Program Files"
        val progFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:/Program Files (x86)"
        val localAppData = System.getenv("LOCALAPPDATA") ?: ""

        val candidates = listOf(
            Triple("File Explorer", "$winDir/explorer.exe", "$winDir/explorer.exe"),
            Triple("Windows Terminal", "wt.exe", "$winDir/System32/cmd.exe"),
            Triple("Microsoft Edge", "$progFilesX86/Microsoft/Edge/Application/msedge.exe", "$progFilesX86/Microsoft/Edge/Application/msedge.exe"),
            Triple("Google Chrome", "$progFiles/Google/Chrome/Application/chrome.exe", "$progFiles/Google/Chrome/Application/chrome.exe"),
            Triple("Google Chrome (x86)", "$progFilesX86/Google/Chrome/Application/chrome.exe", "$progFilesX86/Google/Chrome/Application/chrome.exe"),
            Triple("VS Code", "$localAppData/Programs/Microsoft VS Code/Code.exe", "$localAppData/Programs/Microsoft VS Code/Code.exe"),
            Triple("Task Manager", "$winDir/System32/Taskmgr.exe", "$winDir/System32/Taskmgr.exe"),
            Triple("Notepad", "$winDir/notepad.exe", "$winDir/notepad.exe"),
            Triple("Calculator", "calc.exe", "$winDir/System32/calc.exe"),
            Triple("Command Prompt", "$winDir/System32/cmd.exe", "$winDir/System32/cmd.exe")
        )

        for ((name, exe, icon) in candidates) {
            val file = File(exe)
            val exists = file.exists() || (exe.endsWith(".exe") && !exe.contains("/") && !exe.contains("\\"))
            if (exists) {
                list.add(
                    AppItem(
                        id = "default_${name.lowercase().replace(" ", "_")}",
                        name = name,
                        executablePath = exe,
                        iconPath = if (File(icon).exists()) icon else null
                    )
                )
            }
            if (list.size >= 7) break
        }

        if (list.isEmpty()) {
            list.add(
                AppItem(
                    id = "default_explorer",
                    name = "File Explorer",
                    executablePath = "$winDir/explorer.exe"
                )
            )
        }

        return list
    }
}
