package com.crosspaste.image

import com.luciad.imageio.webp.CompressionType
import com.luciad.imageio.webp.WebPWriteParam
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream

class WebpImageWriter : ImageWriter<BufferedImage> {

    private val logger: KLogger = KotlinLogging.logger {}

    override fun writeImage(
        image: BufferedImage,
        formatName: String,
        imagePath: Path,
    ): Boolean {
        try {
            val writer = ImageIO.getImageWritersByMIMEType("image/webp").next()

            // Configure encoding parameters
            val writeParam =
                (writer.defaultWriteParam as WebPWriteParam).apply {
                    compressionType = CompressionType.Lossy
                    alphaCompressionAlgorithm = 1
                    useSharpYUV = true
                }

            // Configure the output on the ImageWriter
            writer.output = FileImageOutputStream(imagePath.toFile())

            // Encode
            writer.write(null, IIOImage(image, null, null), writeParam)
            return true
        } catch (e: IOException) {
            logger.warn(e) { "Failed to write image to webp" }
            return false
        }
    }
}
