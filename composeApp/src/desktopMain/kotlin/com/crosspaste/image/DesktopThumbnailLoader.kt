package com.crosspaste.image

import com.crosspaste.app.AppFileType
import com.crosspaste.info.PasteInfos.DIMENSIONS
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.createPasteInfoWithoutConverter
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.fileNameRemoveExtension
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class DesktopThumbnailLoader(
    private val userDataPathProvider: UserDataPathProvider,
) : ConcurrentLoader<PasteFileCoordinate, Path>, ThumbnailLoader {

    private val logger = KotlinLogging.logger {}

    override val lockMap: ConcurrentMap<String, PlatformLock> = ConcurrentMap()

    private val fileUtils = getFileUtils()

    private val basePath = userDataPathProvider.resolve(appFileType = AppFileType.IMAGE)

    override fun resolve(
        key: String,
        value: PasteFileCoordinate,
    ): Path {
        return getThumbnailPath(value)
    }

    override fun getThumbnailPath(pasteFileCoordinate: PasteFileCoordinate): Path {
        val relativePath =
            fileUtils.createPasteRelativePath(
                pasteFileCoordinate.toPasteCoordinate(),
                pasteFileCoordinate.filePath.name,
            )

        val thumbnailName = "thumbnail_${pasteFileCoordinate.filePath.fileNameRemoveExtension}.png"

        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = true, isFile = true)
            .toNioPath()
            .resolveSibling(thumbnailName)
            .toOkioPath()
    }

    override fun getOriginMetaPath(pasteFileCoordinate: PasteFileCoordinate): Path {
        val relativePath =
            fileUtils.createPasteRelativePath(
                pasteFileCoordinate.toPasteCoordinate(),
                pasteFileCoordinate.filePath.name,
            )

        val metaProperties = "meta_${pasteFileCoordinate.filePath.fileNameRemoveExtension}.properties"

        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
            .toNioPath()
            .resolveSibling(metaProperties)
            .toOkioPath()
    }

    override fun readOriginMeta(
        pasteFileCoordinate: PasteFileCoordinate,
        imageInfoBuilder: ImageInfoBuilder,
    ) {
        try {
            val properties = Properties()
            properties.load(getOriginMetaPath(pasteFileCoordinate).toNioPath().inputStream().buffered())
            properties.getProperty(DIMENSIONS)?.let {
                imageInfoBuilder.add(createPasteInfoWithoutConverter(DIMENSIONS, it))
            }
        } catch (e: Exception) {
            logger.warn { "Failed to read meta data for file: ${pasteFileCoordinate.filePath}" }
        }
    }

    override fun convertToKey(value: PasteFileCoordinate): String {
        return value.toString()
    }

    override fun save(
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
            val properties = Properties()
            properties.setProperty(SIZE, "$fileSize")
            properties.setProperty(DIMENSIONS, "$originalWidth x $originalHeight")
            properties.store(getOriginMetaPath(value).toNioPath().outputStream().buffered(), null)
        }
    }

    override fun exist(result: Path): Boolean {
        return result.toFile().exists()
    }

    override fun loggerWarning(
        value: PasteFileCoordinate,
        e: Exception,
    ) {
        logger.warn { "Failed to create thumbnail for file: $value" }
    }
}
