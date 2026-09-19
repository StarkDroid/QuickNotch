package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.model.AppItem
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView

object Win32ShellIconLoader {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val fsv: FileSystemView by lazy { FileSystemView.getFileSystemView() }

    private val IID_IImageList = Guid.GUID.fromString("{46EB5926-582E-4017-9FDF-E8998DAA0950}")
    private const val SHIL_JUMBO = 4       // 256x256
    private const val SHIL_EXTRALARGE = 2  // 48x48
    private const val SHIL_LARGE = 0       // 32x32
    private const val SHGFI_SYSICONINDEX = 0x000004000
    private const val ILD_TRANSPARENT = 0x00000001
    private const val DI_NORMAL = 0x0003

    @Structure.FieldOrder("hIcon", "iIcon", "dwAttributes", "szDisplayName", "szTypeName")
    open class SHFILEINFOW : Structure() {
        @JvmField var hIcon: WinDef.HICON? = null
        @JvmField var iIcon: Int = 0
        @JvmField var dwAttributes: Int = 0
        @JvmField var szDisplayName: CharArray = CharArray(260)
        @JvmField var szTypeName: CharArray = CharArray(80)
    }

    private interface Shell32Extra : StdCallLibrary {
        companion object {
            val INSTANCE: Shell32Extra = Native.load("shell32", Shell32Extra::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun SHGetFileInfoW(
            pszPath: String,
            dwFileAttributes: Int,
            psfi: SHFILEINFOW,
            cbFileInfo: Int,
            uFlags: Int
        ): Pointer?

        fun SHGetImageList(
            iImageList: Int,
            riid: Guid.GUID,
            ppv: PointerByReference
        ): WinNT.HRESULT
    }

    private interface User32Extra : StdCallLibrary {
        companion object {
            val INSTANCE: User32Extra = Native.load("user32", User32Extra::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun PrivateExtractIconsW(
            szFileName: String,
            nIconIndex: Int,
            cxIcon: Int,
            cyIcon: Int,
            phicon: Array<WinDef.HICON?>,
            piconid: IntArray?,
            nIcons: Int,
            flags: Int
        ): Int

        fun DrawIconEx(
            hdc: WinDef.HDC,
            xLeft: Int,
            yTop: Int,
            hIcon: WinDef.HICON,
            cxWidth: Int,
            cyWidth: Int,
            istepIfAniCur: Int,
            hbrFlickerFreeDraw: WinDef.HBRUSH?,
            diFlags: Int
        ): Boolean

        fun GetDC(hWnd: WinDef.HWND?): WinDef.HDC
        fun ReleaseDC(hWnd: WinDef.HWND?, hDC: WinDef.HDC): Int
        fun DestroyIcon(hIcon: WinDef.HICON): Boolean
        fun GetIconInfo(hIcon: WinDef.HICON, piconinfo: WinGDI.ICONINFO): Boolean
    }

    private interface ComCtl32Extra : StdCallLibrary {
        companion object {
            val INSTANCE: ComCtl32Extra = Native.load("comctl32", ComCtl32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }

        fun ImageList_GetIcon(
            himl: Pointer,
            i: Int,
            flags: Int
        ): WinDef.HICON?
    }

    fun getCachedIcon(id: String): ImageBitmap? = cache[id]

    suspend fun loadIcon(app: AppItem): ImageBitmap = withContext(Dispatchers.IO) {
        val cached = cache[app.id]
        if (cached != null) return@withContext cached

        val iconBitmap = try {
            val file = cleanPath(app.iconPath ?: app.executablePath)
            if (file != null) {
                // 1. Try Windows Shell ImageList (256x256 Jumbo, then 48x48 Extra Large)
                extractShellImageListIcon(file.absolutePath, SHIL_JUMBO)
                    ?: extractShellImageListIcon(file.absolutePath, SHIL_EXTRALARGE)
                    // 2. Try User32 PrivateExtractIcons (128x128)
                    ?: extractPrivateIcon(file.absolutePath, 128)
                    // 3. Fallback to Java Swing system icon rendered at 128x128
                    ?: run {
                        val icon = fsv.getSystemIcon(file)
                        iconToImageBitmap(icon, 128)
                    }
            } else {
                AppIconLoader.generateMonogramIcon(app.name)
            }
        } catch (_: Throwable) {
            AppIconLoader.generateMonogramIcon(app.name)
        }

        cache[app.id] = iconBitmap
        iconBitmap
    }

    private fun cleanPath(raw: String): File? {
        val trimmed = raw.trim().trim('"', '\'')
        val direct = File(trimmed)
        if (direct.exists()) return direct

        val commaIdx = trimmed.lastIndexOf(',')
        if (commaIdx > 0) {
            val candidate = File(trimmed.substring(0, commaIdx).trim().trim('"', '\''))
            if (candidate.exists()) return candidate
        }
        return null
    }

    private fun extractShellImageListIcon(filePath: String, imageListType: Int): ImageBitmap? {
        return try {
            val sfi = SHFILEINFOW()
            val ret = Shell32Extra.INSTANCE.SHGetFileInfoW(
                filePath,
                0,
                sfi,
                sfi.size(),
                SHGFI_SYSICONINDEX
            )
            if (ret == null) return null

            val ppv = PointerByReference()
            val hr = Shell32Extra.INSTANCE.SHGetImageList(imageListType, IID_IImageList, ppv)
            if (hr.toInt() < 0 || ppv.value == null) return null

            val hIcon = ComCtl32Extra.INSTANCE.ImageList_GetIcon(ppv.value, sfi.iIcon, ILD_TRANSPARENT)
                ?: return null

            try {
                val bImg = hIconToBufferedImage(hIcon)
                bImg?.toComposeImageBitmap()
            } finally {
                User32Extra.INSTANCE.DestroyIcon(hIcon)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractPrivateIcon(filePath: String, size: Int): ImageBitmap? {
        return try {
            val icons = arrayOfNulls<WinDef.HICON>(1)
            val extracted = User32Extra.INSTANCE.PrivateExtractIconsW(
                filePath,
                0,
                size,
                size,
                icons,
                null,
                1,
                0
            )
            val hIcon = icons[0]
            if (extracted > 0 && hIcon != null) {
                try {
                    val bImg = hIconToBufferedImage(hIcon)
                    bImg?.toComposeImageBitmap()
                } finally {
                    User32Extra.INSTANCE.DestroyIcon(hIcon)
                }
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun hIconToBufferedImage(hIcon: WinDef.HICON): BufferedImage? {
        val iconInfo = WinGDI.ICONINFO()
        if (!User32Extra.INSTANCE.GetIconInfo(hIcon, iconInfo)) {
            return null
        }

        try {
            val hBitmap = iconInfo.hbmColor ?: iconInfo.hbmMask ?: return null
            val bm = WinGDI.BITMAP()
            if (GDI32.INSTANCE.GetObject(hBitmap, bm.size(), bm.pointer) == 0) {
                return null
            }
            bm.read()

            val width = bm.bmWidth.toInt()
            val height = if (iconInfo.hbmColor == null) bm.bmHeight.toInt() / 2 else bm.bmHeight.toInt()
            if (width <= 0 || height <= 0) return null

            val hdc = User32Extra.INSTANCE.GetDC(null)
            val memDC = GDI32.INSTANCE.CreateCompatibleDC(hdc)

            val bmi = WinGDI.BITMAPINFO()
            bmi.bmiHeader.biSize = bmi.bmiHeader.size()
            bmi.bmiHeader.biWidth = width
            bmi.bmiHeader.biHeight = -height // top-down DIB
            bmi.bmiHeader.biPlanes = 1
            bmi.bmiHeader.biBitCount = 32
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB

            val pBits = PointerByReference()
            val dib = GDI32.INSTANCE.CreateDIBSection(memDC, bmi, WinGDI.DIB_RGB_COLORS, pBits, null, 0)
            val oldBmp = GDI32.INSTANCE.SelectObject(memDC, dib)

            User32Extra.INSTANCE.DrawIconEx(memDC, 0, 0, hIcon, width, height, 0, null, DI_NORMAL)

            val rawPixels = pBits.value.getIntArray(0, width * height)

            var hasAlpha = false
            for (pixel in rawPixels) {
                if ((pixel ushr 24) != 0) {
                    hasAlpha = true
                    break
                }
            }

            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            if (!hasAlpha) {
                for (i in rawPixels.indices) {
                    val rgb = rawPixels[i] and 0x00FFFFFF
                    if (rgb != 0) {
                        rawPixels[i] = (0xFF shl 24) or rgb
                    }
                }
            }
            bufferedImage.setRGB(0, 0, width, height, rawPixels, 0, width)

            GDI32.INSTANCE.SelectObject(memDC, oldBmp)
            GDI32.INSTANCE.DeleteObject(dib)
            GDI32.INSTANCE.DeleteDC(memDC)
            User32Extra.INSTANCE.ReleaseDC(null, hdc)

            return bufferedImage
        } finally {
            if (iconInfo.hbmColor != null) GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor)
            if (iconInfo.hbmMask != null) GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask)
        }
    }

    private fun iconToImageBitmap(icon: Icon?, targetSize: Int = 64): ImageBitmap {
        if (icon == null) return AppIconLoader.generateMonogramIcon("?")
        val width = icon.iconWidth.coerceAtLeast(1)
        val height = icon.iconHeight.coerceAtLeast(1)
        val bufferedImage = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
        val g2d: Graphics2D = bufferedImage.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val tempImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val tempG2d = tempImage.createGraphics()
            icon.paintIcon(null, tempG2d, 0, 0)
            tempG2d.dispose()
            g2d.drawImage(tempImage, 0, 0, targetSize, targetSize, null)
        } finally {
            g2d.dispose()
        }
        return bufferedImage.toComposeImageBitmap()
    }

}
