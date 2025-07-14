package com.crosspaste.utils

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Bitmap
import coil3.Image
import coil3.asImage
import okio.Path
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.min

actual fun getCoilUtils(): CoilUtils = DesktopCoilUtils

object DesktopCoilUtils : CoilUtils {

    val fileUtils = getFileUtils()

    override fun createBitmap(path: Path): Bitmap =
        path.toFile().inputStream().buffered().use {
            org.jetbrains.skia.Image
                .makeFromEncoded(it.readAllBytes())
                .toComposeImageBitmap()
                .asSkiaBitmap()
        }

    override fun createBitmap(
        path: Path,
        width: Int,
        height: Int,
    ): Bitmap =
        path
            .toFile()
            .inputStream()
            .buffered()
            .use { stream ->
                val originalImage =
                    org.jetbrains.skia.Image
                        .makeFromEncoded(stream.readAllBytes())

                // Determine the actual crop dimensions (in case the image is smaller than the requested crop size)
                val actualCropWidth = min(width, originalImage.width)
                val actualCropHeight = min(height, originalImage.height)

                // Create a surface for the cropped image
                val surface = Surface.makeRasterN32Premul(actualCropWidth, actualCropHeight)

                // Draw only the top-left portion of the original image onto the new surface
                surface.canvas.drawImageRect(
                    originalImage,
                    Rect.makeXYWH(0f, 0f, actualCropWidth.toFloat(), actualCropHeight.toFloat()),
                    Rect.makeWH(actualCropWidth.toFloat(), actualCropHeight.toFloat()),
                )

                // Convert the cropped image to a Compose ImageBitmap
                surface.makeImageSnapshot()
            }.toComposeImageBitmap()
            .asSkiaBitmap()

    override fun asImage(
        bitmap: Bitmap,
        shareable: Boolean,
    ): Image = bitmap.asImage(shareable = shareable)
}
