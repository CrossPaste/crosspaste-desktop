package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinGDI.BITMAP
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference
import java.awt.image.BufferedImage
import java.io.File

data class IconSize(
    val width: Int,
    val height: Int,
)

object JIconExtract {

    private val ICON_SIZES =
        listOf(
            IconSize(512, 512),
            IconSize(256, 256),
            IconSize(128, 128),
            IconSize(96, 96),
            IconSize(64, 64),
            IconSize(48, 48),
            IconSize(32, 32),
            IconSize(16, 16),
        )

    /**
     * @param file File to the requested file
     * @param size width and height of the returned BufferedImage
     * @return BufferedImage for the given File
     */
    fun getIconForFile(
        file: File,
        size: IconSize? = null,
    ): BufferedImage? = getIconForFile(file.absolutePath, size)

    /**
     * @param filePath Path given by String to the requested file
     * @param size width and height of the returned BufferedImage
     * @return BufferedImage for the given File
     */
    fun getIconForFile(
        filePath: String,
        size: IconSize? = null,
    ): BufferedImage? {
        val sizesToTry =
            if (size != null) {
                listOf(size)
            } else {
                ICON_SIZES
            }

        for (iconSize in sizesToTry) {
            val result = tryGetIconForSize(filePath, iconSize)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun tryGetIconForSize(
        filePath: String,
        size: IconSize,
    ): BufferedImage? {
        var hBitmap: HBITMAP? = null
        var hdc: HDC? = null

        return try {
            hBitmap = getHBITMAPForFile(size.width, size.height, filePath) ?: return null

            val bitmap = BITMAP()
            val result = GDI32.INSTANCE.GetObject(hBitmap, bitmap.size(), bitmap.pointer)

            if (result <= 0) {
                return null
            }

            bitmap.read()

            val w = bitmap.bmWidth.toInt()
            val h = bitmap.bmHeight.toInt()

            if (w < size.width / 2 || h < size.height / 2) {
                return null
            }

            hdc = User32.INSTANCE.GetDC(null)

            val bitmapInfo = BITMAPINFO()
            bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size()

            if (0 ==
                GDI32.INSTANCE.GetDIBits(
                    hdc,
                    hBitmap,
                    0,
                    0,
                    Pointer.NULL,
                    bitmapInfo,
                    WinGDI.DIB_RGB_COLORS,
                )
            ) {
                return null
            }

            bitmapInfo.read()

            val lpPixels = Memory(bitmapInfo.bmiHeader.biSizeImage.toLong())

            bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB
            bitmapInfo.bmiHeader.biHeight = -h

            if (0 ==
                GDI32.INSTANCE.GetDIBits(
                    hdc,
                    hBitmap,
                    0,
                    bitmapInfo.bmiHeader.biHeight,
                    lpPixels,
                    bitmapInfo,
                    WinGDI.DIB_RGB_COLORS,
                )
            ) {
                return null
            }

            val colorArray: IntArray = lpPixels.getIntArray(0, w * h)

            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
                setRGB(0, 0, w, h, colorArray, 0, w)
            }
        } catch (_: Exception) {
            null
        } finally {
            hdc?.let { User32.INSTANCE.ReleaseDC(null, it) }
            hBitmap?.let { GDI32.INSTANCE.DeleteObject(it) }
        }
    }

    /**
     *
     * @param width width for the requested HBITMAP
     * @param height height for the requested HBITMAP
     * @param filePath HBITMAP for the given file path
     * @return Windows Native Implementation of HBITMAP (should not be used directly)
     */
    private fun getHBITMAPForFile(
        width: Int,
        height: Int,
        filePath: String,
    ): HBITMAP? {
        val h1 = Ole32.INSTANCE.CoInitialize(null)
        if (!COMUtils.SUCCEEDED(h1)) return null

        try {
            val factory = PointerByReference()

            val h2: HRESULT? =
                Shell32.INSTANCE.SHCreateItemFromParsingName(
                    WString(filePath),
                    null,
                    REFIID(IID("BCC18B79-BA16-442F-80C4-8A59C30C463B")),
                    factory,
                )

            if (COMUtils.SUCCEEDED(h2)) {
                val imageFactory = IShellItemImageFactory(factory.value)

                val hBitmapPointer = PointerByReference()

                val h3 = imageFactory.GetImage(SIZEByValue(width, height), 0, hBitmapPointer)

                var bitmap: HBITMAP? = null
                if (COMUtils.SUCCEEDED(h3)) {
                    bitmap = HBITMAP(hBitmapPointer.value)
                }

                imageFactory.Release()
                return bitmap
            }

            return null
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }
}
