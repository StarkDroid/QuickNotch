package com.starkdroid.quicknotch.media

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts a packaged native helper (compiled macOS bridge, Windows PowerShell
 * script) from the jar resources to a temp file so it can be executed. The
 * macOS binary only exists when the project was built on a macOS host; a
 * missing resource is a normal outcome on other hosts and yields no session.
 */
internal object NativeResources {

    private val extractedCache = ConcurrentHashMap<String, File?>()

    fun extract(resourcePath: String, suffix: String): File? =
        extractedCache.computeIfAbsent(resourcePath) {
            try {
                val stream = NativeResources::class.java.classLoader
                    ?.getResourceAsStream(resourcePath) ?: return@computeIfAbsent null
                val file = File.createTempFile("quicknotch-", suffix).apply { deleteOnExit() }
                stream.use { input -> file.outputStream().use(input::copyTo) }
                file.setExecutable(true)
                file
            } catch (_: Throwable) {
                null
            }
        }
}
