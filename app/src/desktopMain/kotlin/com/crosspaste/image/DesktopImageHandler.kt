package com.crosspaste.image

import androidx.compose.ui.unit.IntSize
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.bmp.BmpHeaderDirectory
import com.drew.metadata.gif.GifHeaderDirectory
import com.drew.metadata.heif.HeifDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.photoshop.PsdHeaderDirectory
import com.drew.metadata.png.PngDirectory
import com.drew.metadata.webp.WebpDirectory
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.Source
import okio.Path
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private typealias SizeExtractor = (Metadata) -> IntSize?

object DesktopImageHandler : ImageHandler<BufferedImage> {

    private val fileUtils = getFileUtils()

    private val specialFormatWriteMap: Map<String, ImageWriter<BufferedImage>> =
        mapOf(
            "webp" to WebpImageWriter(),
        )

    private val extractors =
        listOf<SizeExtractor>(
            // JPEG
            { m -> m.dir<JpegDirectory> { IntSize(it.imageWidth, it.imageHeight) } },
            // PNG
            { m ->
                m.dir<PngDirectory> {
                    IntSize(
                        it.getInt(PngDirectory.TAG_IMAGE_WIDTH),
                        it.getInt(PngDirectory.TAG_IMAGE_HEIGHT),
                    )
                }
            },
            // WebP
            { m ->
                m.dir<WebpDirectory> {
                    IntSize(
                        it.getInt(WebpDirectory.TAG_IMAGE_WIDTH),
                        it.getInt(WebpDirectory.TAG_IMAGE_HEIGHT),
                    )
                }
            },
            // GIF
            { m ->
                m.dir<GifHeaderDirectory> {
                    IntSize(
                        it.getInt(GifHeaderDirectory.TAG_IMAGE_WIDTH),
                        it.getInt(GifHeaderDirectory.TAG_IMAGE_HEIGHT),
                    )
                }
            },
            // BMP
            { m ->
                m.dir<BmpHeaderDirectory> {
                    IntSize(
                        it.getInt(BmpHeaderDirectory.TAG_IMAGE_WIDTH),
                        it.getInt(BmpHeaderDirectory.TAG_IMAGE_HEIGHT),
                    )
                }
            },
            // HEIC / AVIF
            { m ->
                m.dir<HeifDirectory> {
                    IntSize(
                        it.getInt(HeifDirectory.TAG_IMAGE_WIDTH),
                        it.getInt(HeifDirectory.TAG_IMAGE_HEIGHT),
                    )
                }
            },
            // PSD
            { m ->
                m.dir<PsdHeaderDirectory> {
                    IntSize(
                        it.getInt(PsdHeaderDirectory.TAG_IMAGE_WIDTH),
                        it.getInt(PsdHeaderDirectory.TAG_IMAGE_HEIGHT),
                    )
                }
            },
        )

    override fun writeImage(
        image: BufferedImage,
        formatName: String,
        imagePath: Path,
    ): Boolean =
        specialFormatWriteMap[formatName]?.writeImage(image, formatName, imagePath) ?: run {
            fileUtils.createDir(imagePath.noOptionParent)
            if (!ImageIO.write(image, formatName, imagePath.toFile())) {
                val convertedImage =
                    BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

                val g2d = convertedImage.createGraphics()
                g2d.drawImage(image, 0, 0, null)
                g2d.dispose()

                ImageIO.write(convertedImage, formatName, imagePath.toFile())
            } else {
                true
            }
        }

    override fun readImage(imagePath: Path): BufferedImage? =
        runCatching {
            ImageIO.read(imagePath.toFile())
        }.getOrNull()

    override fun readImage(source: Source): BufferedImage? = readImage(ByteReadChannel(source))

    override fun readImage(byteReadChannel: ByteReadChannel): BufferedImage? =
        runCatching {
            ImageIO.read(byteReadChannel.toInputStream())
        }.getOrNull()

    override fun readSize(imagePath: Path): IntSize? =
        runCatching {
            val metadata = ImageMetadataReader.readMetadata(imagePath.toFile())
            extractors.firstNotNullOfOrNull { extractor -> extractor(metadata) }
        }.getOrNull()

    private inline fun <reified T : Directory> Metadata.dir(block: (T) -> IntSize?): IntSize? =
        runCatching {
            val directory = this.getFirstDirectoryOfType(T::class.java)
            if (directory != null) {
                block(directory)
            } else {
                null
            }
        }.getOrNull()
}
