package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Loads macOS application icons in-process from the `.icns` packed into each app
 * bundle, so refreshing a full list never spawns dozens of `sips`/`qlmanage`
 * sub-processes (those used to time out under concurrency and fall back to
 * monograms). The large representations inside a modern `.icns` are bare PNG
 * chunks that [ImageIO] can decode directly; the shell utilities remain only as
 * a last resort for legacy JPEG2000-only archives or bundles without any `.icns`.
 */
object MacAppIconLoader {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val iconCacheDir: File by lazy {
        val dir = File(System.getProperty("java.io.tmpdir"), "quicknotch-icons")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /** ICNS chunk type -> pixel size for the PNG representations an archive can hold. */
    private val ICNS_PNG_SIZES = mapOf(
        "ic04" to 16, "icp4" to 16, "ic05" to 32, "icp5" to 32, "icp6" to 64,
        "ic11" to 64, "ic07" to 128, "ic08" to 256, "ic12" to 256,
        "ic09" to 512, "ic13" to 512, "ic10" to 1024, "ic14" to 1024
    )

    fun getCachedIcon(id: String): ImageBitmap? = cache[id]

    suspend fun loadIcon(app: AppItem): ImageBitmap = withContext(Dispatchers.IO) {
        cache[app.id]?.let { return@withContext it }

        val bitmap = try {
            val icns = findIcnsFile(app)
            parseIcnsPng(icns)
                ?: icns?.let { convertWithSips(it) }
                ?: previewWithQuickLook(File(app.iconPath ?: app.executablePath))
                ?: AppIconLoader.generateMonogramIcon(app.name)
        } catch (_: Throwable) {
            AppIconLoader.generateMonogramIcon(app.name)
        }

        cache[app.id] = bitmap
        bitmap
    }

    private fun findIcnsFile(app: AppItem): File? {
        val iconHint = app.iconPath?.let { File(it) }
        if (iconHint != null && iconHint.isFile && iconHint.extension.equals("icns", ignoreCase = true)) {
            return iconHint
        }

        val bundle = File(app.executablePath).takeIf { it.isDirectory && it.extension.equals("app", true) }
            ?: iconHint?.takeIf { it.isDirectory && it.extension.equals("app", true) }
            ?: return iconHint?.takeIf { it.isFile }

        val resources = File(bundle, "Contents/Resources")
        val iconFileName = MacInstalledAppsScanner.readBundleInfo(bundle, "CFBundleIconFile")
        if (!iconFileName.isNullOrBlank()) {
            val named = if (iconFileName.endsWith(".icns", ignoreCase = true)) iconFileName else "$iconFileName.icns"
            val candidate = File(resources, named)
            if (candidate.isFile) return candidate
        }

        return resources.takeIf { it.isDirectory }
            ?.listFiles { file -> file.isFile && file.extension.equals("icns", ignoreCase = true) }
            ?.maxByOrNull { it.length() }
    }

    /**
     * Reads the largest PNG embedded in an `.icns` archive and decodes it with
     * [ImageIO], avoiding a sub-process per app. Modern macOS icons store every
     * scale as a bare PNG chunk; legacy archives may keep JPEG2000 data instead,
     * in which case this returns null and [convertWithSips] takes over.
     */
    private fun parseIcnsPng(icns: File?): ImageBitmap? {
        if (icns == null || !icns.isFile) return null

        val bytes = try {
            icns.readBytes()
        } catch (_: Throwable) {
            return null
        }
        if (bytes.size < 8 ||
            (bytes[0].toInt() and 0xFF) != 0x69 || (bytes[1].toInt() and 0xFF) != 0x63 ||
            (bytes[2].toInt() and 0xFF) != 0x6E || (bytes[3].toInt() and 0xFF) != 0x73
        ) return null

        var offset = 8
        var bestSize = -1
        var bestData: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val type = String(bytes, offset, 4, Charsets.US_ASCII)
            val length = ((bytes[offset + 4].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 5].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 6].toInt() and 0xFF) shl 8) or
                (bytes[offset + 7].toInt() and 0xFF)
            if (length < 8 || offset + length > bytes.size) break

            val size = ICNS_PNG_SIZES[type]
            if (size != null && size > bestSize && length > 12 &&
                (bytes[offset + 8].toInt() and 0xFF) == 0x89 &&
                (bytes[offset + 9].toInt() and 0xFF) == 0x50 &&
                (bytes[offset + 10].toInt() and 0xFF) == 0x4E &&
                (bytes[offset + 11].toInt() and 0xFF) == 0x47
            ) {
                bestSize = size
                bestData = bytes.copyOfRange(offset + 8, offset + length)
            }
            offset += length
        }

        val buffered = bestData?.let { data ->
            runCatching { ImageIO.read(ByteArrayInputStream(data)) }.getOrNull()
        }
        return buffered?.toComposeImageBitmap()
    }

    private fun convertWithSips(icns: File, size: Int = 128): ImageBitmap? {
        // Include the source path: many apps ship an identical "AppIcon.icns"
        // with the same timestamp, which used to collide on one cache entry.
        val out = File(iconCacheDir, "${icns.absolutePath.hashCode()}-${icns.name}-${icns.lastModified()}-$size.png")
        if (out.isFile && out.length() > 0) {
            return ImageIO.read(out)?.toComposeImageBitmap()
        }

        val process = ProcessBuilder(
            "sips",
            "-z", size.toString(), size.toString(),
            "-s", "format", "png",
            icns.absolutePath,
            "--out", out.absolutePath
        ).redirectErrorStream(true).start()

        if (!process.waitFor(4, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0 || !out.isFile || out.length() == 0L) {
            return null
        }
        return ImageIO.read(out)?.toComposeImageBitmap()
    }

    private fun previewWithQuickLook(target: File, size: Int = 128): ImageBitmap? {
        if (!target.exists()) return null
        val stamp = target.lastModified()
        val outName = "${target.name}-$stamp-$size.png"
        val expected = File(iconCacheDir, outName)
        if (expected.isFile && expected.length() > 0) {
            return ImageIO.read(expected)?.toComposeImageBitmap()
        }

        val process = ProcessBuilder(
            "qlmanage", "-t", "-s", size.toString(), "-o", iconCacheDir.absolutePath, target.absolutePath
        ).redirectErrorStream(true).start()

        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }

        val generated = iconCacheDir.listFiles { file ->
            file.isFile && file.name.startsWith(target.name) && file.extension.equals("png", true)
        }?.maxByOrNull { it.lastModified() } ?: return null

        if (generated != expected) {
            generated.copyTo(expected, overwrite = true)
        }
        return ImageIO.read(expected)?.toComposeImageBitmap()
    }
}
