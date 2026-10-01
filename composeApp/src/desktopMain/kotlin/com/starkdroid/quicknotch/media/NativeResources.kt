package com.starkdroid.quicknotch.media

import java.io.File

/**
 * Extracts a packaged native helper (compiled macOS bridge, Windows PowerShell
 * script) from the jar resources to a temp file so it can be executed. The
 * macOS binary only exists when the project was built on a macOS host; a
 * missing resource is a normal outcome on other hosts and yields no session.
 */
internal object NativeResources {

    fun extract(resourcePath: String, suffix: String): File? {
        return try {
            val stream = NativeResources::class.java.classLoader
                ?.getResourceAsStream(resourcePath) ?: return null
            val file = File.createTempFile("quicknotch-", suffix).apply { deleteOnExit() }
            stream.use { input -> file.outputStream().use(input::copyTo) }
            file.setExecutable(true)
            file
        } catch (_: Throwable) {
            null
        }
    }
}
