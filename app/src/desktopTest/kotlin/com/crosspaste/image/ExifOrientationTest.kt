package com.crosspaste.image

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.request.Options
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExifOrientationTest {

    private val width = 40
    private val height = 20

    // Left half red, right half blue, stored landscape.
    private fun landscapeJpeg(): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (x < width / 2) 0xFF0000 else 0x0000FF)
            }
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    // Inserts an APP1 Exif segment holding only the Orientation tag right after SOI.
    private fun withOrientation(
        jpeg: ByteArray,
        orientation: Int,
    ): ByteArray {
        val tiff =
            byteArrayOf(
                0x4D,
                0x4D,
                0x00,
                0x2A,
                0x00,
                0x00,
                0x00,
                0x08, // big-endian header, IFD0 at 8
                0x00,
                0x01, // one entry
                0x01,
                0x12,
                0x00,
                0x03,
                0x00,
                0x00,
                0x00,
                0x01, // Orientation, SHORT, count 1
                0x00,
                orientation.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00, // no next IFD
            )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    @Test
    fun coilDecode_appliesExifOrientation() =
        runTest {
            // The preview relies on Skia applying EXIF orientation while decoding; readSize
            // below must agree with it. Guards against a Skia/Coil upgrade changing that.
            val source = ImageSource(Buffer().write(withOrientation(landscapeJpeg(), 6)), FileSystem.SYSTEM)
            val bitmap =
                (SkiaImageDecoder(source, Options(PlatformContext.INSTANCE)).decode().image as BitmapImage).bitmap

            assertEquals(height, bitmap.width)
            assertEquals(width, bitmap.height)
            // Rotating 90° clockwise turns the left (red) half into the top half.
            val top = bitmap.getColor(height / 2, 5)
            assertTrue(Color.getR(top) > 200 && Color.getB(top) < 60)
        }

    @Test
    fun readSize_reportsDisplaySizeForRotatedJpeg() =
        runTest {
            val dir = createTempDirectory("exif").toFile()
            try {
                val rotated = File(dir, "rotated.jpg").apply { writeBytes(withOrientation(landscapeJpeg(), 6)) }
                val mirrored = File(dir, "mirrored.jpg").apply { writeBytes(withOrientation(landscapeJpeg(), 2)) }
                val plain = File(dir, "plain.jpg").apply { writeBytes(landscapeJpeg()) }

                val rotatedSize = DesktopImageHandler.readSize(rotated.toOkioPath())!!
                val mirroredSize = DesktopImageHandler.readSize(mirrored.toOkioPath())!!
                val plainSize = DesktopImageHandler.readSize(plain.toOkioPath())!!

                assertEquals(height to width, rotatedSize.width to rotatedSize.height)
                assertEquals(width to height, mirroredSize.width to mirroredSize.height)
                assertEquals(width to height, plainSize.width to plainSize.height)
            } finally {
                dir.deleteRecursively()
            }
        }
}
