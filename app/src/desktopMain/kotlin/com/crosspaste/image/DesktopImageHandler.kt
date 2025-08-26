package com.crosspaste.image

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.Source
import okio.Path
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object DesktopImageHandler : ImageHandler<BufferedImage> {

    private val fileUtils = getFileUtils()

    private val specialFormatWriteMap: Map<String, ImageWriter<BufferedImage>> =
        mapOf(
            "webp" to WebpImageWriter(),
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
}
