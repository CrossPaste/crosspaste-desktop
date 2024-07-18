package com.crosspaste.os.windows

import com.crosspaste.os.windows.api.Shell32
import com.crosspaste.os.windows.api.User32
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
import java.nio.file.Path

object JIconExtract {
    /**
     *
     * @param width width of the returned BufferedImage
     * @param height height of the returned BufferedImage
     * @param file Path to the requested file
     * @return BufferedImage for the given File
     */
    fun getIconForFile(
        width: Int,
        height: Int,
        file: Path,
    ): BufferedImage? {
        return getIconForFile(width, height, file.toString())
    }

    /**
     *
     * @param width width of the returned BufferedImage
     * @param height height of the returned BufferedImage
     * @param file File to the requested file
     * @return BufferedImage for the given File
     */
    fun getIconForFile(
        width: Int,
        height: Int,
        file: File,
    ): BufferedImage? {
        return getIconForFile(width, height, file.absolutePath)
    }

    /**
     *
     * @param width width of the returned BufferedImage
     * @param height height of the returned BufferedImage
     * @param fileName Path given by String to the requested file
     * @return BufferedImage for the given File
     */
    fun getIconForFile(
        width: Int,
        height: Int,
        fileName: String?,
    ): BufferedImage? {
        val hbitmap = getHBITMAPForFile(width, height, fileName)

        val bitmap = BITMAP()

        try {
            val s: Int = GDI32.INSTANCE.GetObject(hbitmap, bitmap.size(), bitmap.pointer)

            if (s > 0) {
                bitmap.read()

                val w = bitmap.bmWidth.toInt()
                val h = bitmap.bmHeight.toInt()

                val hdc: HDC = User32.INSTANCE.GetDC(null)

                val bitmapinfo = BITMAPINFO()

                bitmapinfo.bmiHeader.biSize = bitmapinfo.bmiHeader.size()

                require(
                    0 !=
                        GDI32.INSTANCE.GetDIBits(
                            hdc,
                            hbitmap,
                            0,
                            0,
                            Pointer.NULL,
                            bitmapinfo,
                            WinGDI.DIB_RGB_COLORS,
                        ),
                ) { "GetDIBits should not return 0" }

                bitmapinfo.read()

                val lpPixels: Memory = Memory(bitmapinfo.bmiHeader.biSizeImage.toLong())

                bitmapinfo.bmiHeader.biCompression = WinGDI.BI_RGB
                bitmapinfo.bmiHeader.biHeight = -h

                require(
                    0 !=
                        GDI32.INSTANCE.GetDIBits(
                            hdc,
                            hbitmap,
                            0,
                            bitmapinfo.bmiHeader.biHeight,
                            lpPixels,
                            bitmapinfo,
                            WinGDI.DIB_RGB_COLORS,
                        ),
                ) { "GetDIBits should not return 0" }

                val colorArray: IntArray = lpPixels.getIntArray(0, w * h)

                val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                bi.setRGB(0, 0, w, h, colorArray, 0, w)

                return bi
            }
        } finally {
            GDI32.INSTANCE.DeleteObject(hbitmap)
        }

        return null
    }

    /**
     *
     * @param width width for the requested HBITMAP
     * @param height height for the requested HBITMAP
     * @param fileName HBITMAP for the given File
     * @return Windows Native Implementation of HBITMAP (should not be used directly)
     */
    fun getHBITMAPForFile(
        width: Int,
        height: Int,
        fileName: String?,
    ): HBITMAP? {
        val h1 = Ole32.INSTANCE.CoInitialize(null)

        if (COMUtils.SUCCEEDED(h1)) {
            val factory = PointerByReference()

            val h2: HRESULT? =
                Shell32.INSTANCE.SHCreateItemFromParsingName(
                    WString(fileName),
                    null,
                    REFIID(IID("BCC18B79-BA16-442F-80C4-8A59C30C463B")),
                    factory,
                )

            if (COMUtils.SUCCEEDED(h2)) {
                val imageFactory = IShellItemImageFactory(factory.value)

                val hbitmapPointer = PointerByReference()

                val h3 = imageFactory.GetImage(SIZEByValue(width, height), 0, hbitmapPointer)

                if (COMUtils.SUCCEEDED(h3)) {
                    val bitmap = HBITMAP(hbitmapPointer.value)

                    return bitmap
                }

                imageFactory.Release()
            }
        }

        return null
    }
}
