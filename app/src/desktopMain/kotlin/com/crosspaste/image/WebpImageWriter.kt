package com.crosspaste.image

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import com.luciad.imageio.webp.CompressionType
import com.luciad.imageio.webp.WebPWriteParam
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream

class WebpImageWriter : ImageWriter<BufferedImage> {

    private val logger: KLogger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override fun writeImage(
        image: BufferedImage,
        formatName: String,
        imagePath: Path,
    ): Boolean =
        runCatching {
            val writer = ImageIO.getImageWritersByMIMEType("image/webp").next()
            try {
                // Configure encoding parameters
                val writeParam =
                    (writer.defaultWriteParam as WebPWriteParam).apply {
                        compressionType = CompressionType.Lossy
                        alphaCompressionAlgorithm = 1
                        useSharpYUV = true
                    }

                fileUtils.createDir(imagePath.noOptionParent)

                // Configure the output on the ImageWriter and encode
                val output = FileImageOutputStream(imagePath.toFile())
                try {
                    writer.output = output
                    writer.write(null, IIOImage(image, null, null), writeParam)
                } finally {
                    output.close()
                }
                true
            } finally {
                writer.dispose()
            }
        }.getOrElse {
            logger.warn(it) { "Failed to write image to webp" }
            false
        }
}
