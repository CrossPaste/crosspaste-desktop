package com.crosspaste.image

import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createConcurrentPlatformMap
import com.crosspaste.utils.fileNameRemoveExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object DesktopThumbnailLoader : ConcurrentLoader<Path, Path>, ThumbnailLoader {

    private val logger = KotlinLogging.logger {}

    override val lockMap: ConcurrentPlatformMap<String, PlatformLock> = createConcurrentPlatformMap()

    override fun resolve(
        key: String,
        value: Path,
    ): Path {
        return getThumbnailPath(value)
    }

    override fun getThumbnailPath(path: Path): Path {
        return path
            .toNioPath()
            .resolveSibling("thumbnail_${path.fileNameRemoveExtension}.png")
            .toOkioPath()
    }

    override fun convertToKey(value: Path): String {
        return value.toString()
    }

    override fun save(
        key: String,
        value: Path,
        result: Path,
    ) {
        // Decode the image from the file path
        val bytes = value.toNioPath().readBytes()
        val originalImage = Image.makeFromEncoded(bytes)

        // Retrieve the original dimensions of the image
        val originalWidth = originalImage.width
        val originalHeight = originalImage.height

        val thumbnailWidth: Int
        val thumbnailHeight: Int

        // Determine thumbnail dimensions maintaining the aspect ratio
        if (originalWidth <= originalHeight) {
            // For vertical (portrait) images
            thumbnailWidth = 200
            thumbnailHeight = 200 * originalHeight / originalWidth
        } else {
            // For horizontal (landscape) images
            thumbnailWidth = 200 * originalWidth / originalHeight
            thumbnailHeight = 200
        }

        // Create a new Surface for drawing the thumbnail
        val surface = Surface.makeRasterN32Premul(thumbnailWidth, thumbnailHeight)
        val canvas = surface.canvas

        // Calculate the scaling factor to maintain aspect ratio
        val scale =
            minOf(
                thumbnailWidth.toFloat() / originalWidth,
                thumbnailHeight.toFloat() / originalHeight,
            )

        // Calculate the starting point to center the image
        val dx = (thumbnailWidth - originalWidth * scale) / 2
        val dy = (thumbnailHeight - originalHeight * scale) / 2

        // Draw the thumbnail image
        canvas.drawImageRect(
            originalImage,
            org.jetbrains.skia.Rect.makeWH(originalImage.width.toFloat(), originalImage.height.toFloat()),
            org.jetbrains.skia.Rect.makeXYWH(dx, dy, originalImage.width * scale, originalImage.height * scale),
            null,
        )

        // Save the thumbnail image to a file
        surface.makeImageSnapshot().encodeToData()?.bytes?.let {
            result.toNioPath().writeBytes(it)
        }
    }

    override fun exist(result: Path): Boolean {
        return result.toFile().exists()
    }

    override fun loggerWarning(
        value: Path,
        e: Exception,
    ) {
        logger.warn { "Failed to create thumbnail for file: $value" }
    }
}
