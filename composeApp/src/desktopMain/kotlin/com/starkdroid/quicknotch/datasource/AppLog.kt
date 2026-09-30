package com.starkdroid.quicknotch.datasource

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostics that outlive the terminal.
 *
 * The startup lines ("skiko render backend = METAL", the passthrough state) all
 * go to stderr, which is perfect for a Gradle run and worthless for the packaged
 * app: a jpackage `.app` has no console anyone is watching, so a failure the user
 * can only describe as "it did nothing" is also a failure we cannot read. The
 * file lands in the same app-data dir as the config, so the user can open it and
 * so can we, after the fact.
 *
 * Deliberately append-only and self-trimming rather than a logging framework: the
 * app has no log file today, and the only thing that needs one is the handful of
 * failures a user can hit but not see.
 */
object AppLog {

    private const val FILE_NAME = "quicknotch.log"
    private const val MAX_BYTES = 256L * 1024L
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** The file behind [line]; also handy for pointing a user at. */
    val file: File get() = File(AppPaths.configDir, FILE_NAME)

    /** Print to stderr (the Gradle-run case) *and* keep it on disk. */
    fun line(message: String) {
        val text = "QuickNotch: $message"
        System.err.println(text)
        runCatching {
            val target = file
            target.parentFile?.mkdirs()
            // Truncate rather than rotate: two files means guessing which half
            // matters, and the interesting line is always the most recent one.
            if (target.length() > MAX_BYTES) target.writeText("")
            target.appendText("${LocalDateTime.now().format(stamp)} $text\n")
        }
    }
}
