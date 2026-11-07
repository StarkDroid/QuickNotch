package com.starkdroid.quicknotch.datasource

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** File-level plumbing for the updater, kept out of [AppUpdates]. */
internal object FileDigest {

    /** A release without a checksum still installs; a mismatched checksum never does. */
    fun verify(file: File, expected: String?) {
        if (expected.isNullOrBlank()) return
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            error("Checksum mismatch — the download may be corrupt")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Stream a response to disk, reporting coarse progress.
 * The loop re-reads at the bottom: EOF and a short read are both normal here.
 */
internal suspend fun streamToFile(
    channel: ByteReadChannel,
    target: File,
    total: Long,
    onProgress: (Float) -> Unit
) {
    FileOutputStream(target).use { output ->
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        var lastPercent = -1
        var read = channel.readAvailable(buffer, 0, buffer.size)
        while (read >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read)
                done += read
                lastPercent = reportProgress(done, total, lastPercent, onProgress)
            }
            read = channel.readAvailable(buffer, 0, buffer.size)
        }
    }
}

/** Whole-percent progress; reports only when the integer percent changes. */
private fun reportProgress(
    done: Long,
    total: Long,
    lastPercent: Int,
    onProgress: (Float) -> Unit
): Int {
    if (total <= 0) return lastPercent
    val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
    if (percent != lastPercent) onProgress(percent / 100f)
    return percent
}
