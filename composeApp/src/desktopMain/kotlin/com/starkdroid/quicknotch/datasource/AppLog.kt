package com.starkdroid.quicknotch.datasource

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Stderr is invisible in a packaged app, so diagnostics also land in quicknotch.log in the config dir. */
object AppLog {

    private const val FILE_NAME = "quicknotch.log"
    private const val MAX_BYTES = 256L * 1024L
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** The file behind [line]. */
    val file: File get() = File(AppPaths.configDir, FILE_NAME)

    fun line(message: String) {
        val text = "QuickNotch: $message"
        System.err.println(text)
        runCatching {
            val target = file
            target.parentFile?.mkdirs()
            // Truncate rather than rotate: the interesting line is always the most recent.
            if (target.length() > MAX_BYTES) target.writeText("")
            target.appendText("${LocalDateTime.now().format(stamp)} $text\n")
        }
    }
}
