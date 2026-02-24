package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinGDI.BITMAP
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO
import com.sun.jna.platform.win32.WinGDI.ICONINFO
import com.sun.jna.platform.win32.WinNT.HANDLE
import okio.Path
import java.awt.image.BufferedImage
import java.util.Optional
import javax.imageio.ImageIO

object WindowsIconUtils {

    private val user32 = User32.INSTANCE

    fun extractAndSaveIcon(
        filePath: Path,
        outputPath: Path,
    ) {
        val filePathString = filePath.normalized().toString()
        val outputFile = outputPath.toFile()
        JIconExtract.getIconForFile(filePathString)?.let { icon ->
            ImageIO.write(icon, "png", outputFile)
            return@extractAndSaveIcon
        }

        val largeIcons = arrayOfNulls<HICON>(1)
        val smallIcons = arrayOfNulls<HICON>(1)
        val iconCount =
            Shell32.INSTANCE.ExtractIconEx(
                filePathString,
                0,
                largeIcons,
                smallIcons,
                1,
            )

        try {
            if (iconCount > 0 && largeIcons[0] != null) {
                val icon = largeIcons[0]!!

                hiconToImage(icon)?.let { image ->
                    ImageIO.write(image, "png", outputFile)
                }
            }
        } finally {
            largeIcons[0]?.let { user32.DestroyIcon(it) }
            smallIcons[0]?.let { user32.DestroyIcon(it) }
        }
    }

    private fun hiconToImage(hicon: HICON): BufferedImage? {
        var bitmapHandle: HBITMAP? = null
        var deviceContext: HDC? = null
        val gdi32 = GDI32.INSTANCE

        return runCatching {
            val info = ICONINFO()
            if (!user32.GetIconInfo(hicon, info)) return null

            info.read()
            bitmapHandle = Optional.ofNullable(info.hbmColor).orElse(info.hbmMask)

            val bitmap = BITMAP()
            if (gdi32.GetObject(bitmapHandle, bitmap.size(), bitmap.pointer) > 0) {
                bitmap.read()

                val width = bitmap.bmWidth.toInt()
                val height = bitmap.bmHeight.toInt()

                deviceContext = user32.GetDC(null)
                deviceContext?.let { dc ->
                    val bitmapInfo = BITMAPINFO()

                    bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size()
                    require(
                        gdi32.GetDIBits(
                            dc,
                            bitmapHandle,
                            0,
                            0,
                            Pointer.NULL,
                            bitmapInfo,
                            WinGDI.DIB_RGB_COLORS,
                        ) != 0,
                    ) { "GetDIBits should not return 0" }

                    bitmapInfo.read()

                    val pixels = Memory(bitmapInfo.bmiHeader.biSizeImage.toLong())
                    bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB
                    bitmapInfo.bmiHeader.biHeight = -height

                    require(
                        gdi32.GetDIBits(
                            dc,
                            bitmapHandle,
                            0,
                            bitmapInfo.bmiHeader.biHeight,
                            pixels,
                            bitmapInfo,
                            WinGDI.DIB_RGB_COLORS,
                        ) != 0,
                    ) { "GetDIBits should not return 0" }

                    val colorArray = pixels.getIntArray(0, width * height)
                    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    image.setRGB(0, 0, width, height, colorArray, 0, width)

                    image
                }
            } else {
                null
            }
        }.apply {
            deviceContext?.let { user32.ReleaseDC(null, it) }
            Optional
                .ofNullable(bitmapHandle)
                .ifPresent { hObject: HANDLE? -> gdi32.DeleteObject(hObject) }
        }.getOrNull()
    }
}
