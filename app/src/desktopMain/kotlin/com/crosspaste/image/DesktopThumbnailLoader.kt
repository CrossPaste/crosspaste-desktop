package com.crosspaste.image

import com.crosspaste.info.PasteInfos.DIMENSIONS
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.createPasteInfoWithoutConverter
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class DesktopThumbnailLoader(
    private val platform: Platform,
    userDataPathProvider: UserDataPathProvider,
) : AbstractThumbnailLoader(userDataPathProvider) {

    override val logger = KotlinLogging.logger {}

    override val thumbnailSize = 200

    override fun readOriginMeta(
        pasteFileCoordinate: PasteFileCoordinate,
        imageInfoBuilder: ImageInfoBuilder,
    ) {
        runCatching {
            val properties = Properties()
            properties.load(getOriginMetaPath(pasteFileCoordinate).toNioPath().inputStream().buffered())
            properties.getProperty(DIMENSIONS)?.let {
                imageInfoBuilder.add(createPasteInfoWithoutConverter(DIMENSIONS, it))
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to read meta data for file: ${pasteFileCoordinate.filePath}" }
        }
    }

    override fun save(
        key: String,
        value: PasteFileCoordinate,
        result: Path,
    ) {
        if (platform.isMacos()) {
            val metadataPath = getOriginMetaPath(value)
            logExecutionTime(logger, "Create thumbnail by mac api for file: ${value.filePath}") {
                if (!MacAppUtils.createThumbnail(
                        value.filePath.toString(),
                        result.toString(),
                        metadataPath.toString(),
                    )
                ) {
                    defaultSave(key, value, result)
                }
            }
        } else {
            logExecutionTime(logger, "Create thumbnail by default api for file: ${value.filePath}") {
                defaultSave(key, value, result)
            }
        }
    }

    private fun defaultSave(
        key: String,
        value: PasteFileCoordinate,
        result: Path,
    ) {
        // Decode the image from the file path
        val bytes = value.filePath.toNioPath().readBytes()
        val fileSize = bytes.size
        val originalImage = Image.makeFromEncoded(bytes)

        // Retrieve the original dimensions of the image
        val originalWidth = originalImage.width
        val originalHeight = originalImage.height

        val thumbnailWidth: Int
        val thumbnailHeight: Int

        // Determine thumbnail dimensions maintaining the aspect ratio
        if (originalWidth <= originalHeight) {
            // For vertical (portrait) images
            thumbnailWidth = thumbnailSize
            thumbnailHeight = thumbnailSize * originalHeight / originalWidth
        } else {
            // For horizontal (landscape) images
            thumbnailWidth = thumbnailSize * originalWidth / originalHeight
            thumbnailHeight = thumbnailSize
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
            val properties = Properties()
            properties.setProperty(SIZE, "$fileSize")
            properties.setProperty(DIMENSIONS, "$originalWidth x $originalHeight")
            properties.store(getOriginMetaPath(value).toNioPath().outputStream().buffered(), null)
        }
    }
}
