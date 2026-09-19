package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Loads macOS application icons from `.icns` resources or Quick Look previews.
 * Uses built-in `sips` / `qlmanage` so the rest of the app stays in Kotlin.
 */
object MacAppIconLoader {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val iconCacheDir: File by lazy {
        val dir = File(System.getProperty("java.io.tmpdir"), "quicknotch-icons")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun getCachedIcon(id: String): ImageBitmap? = cache[id]

    suspend fun loadIcon(app: AppItem): ImageBitmap = withContext(Dispatchers.IO) {
        cache[app.id]?.let { return@withContext it }

        val bitmap = try {
            val icns = findIcnsFile(app)
            val fromIcns = icns?.let { convertWithSips(it) }
            fromIcns ?: previewWithQuickLook(File(app.iconPath ?: app.executablePath))
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

    private fun convertWithSips(icns: File, size: Int = 128): ImageBitmap? {
        val out = File(iconCacheDir, "${icns.nameWithoutExtension}-${icns.lastModified()}-$size.png")
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
