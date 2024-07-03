package com.crosspaste.image

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

object ImageService : ImageWriter {

    private val specialFormatWriteMap: Map<String, ImageWriter> =
        mapOf(
            "webp" to WebpImageWriter(),
        )

    override fun writeImage(
        image: BufferedImage,
        formatName: String,
        imagePath: Path,
    ): Boolean {
        specialFormatWriteMap[formatName]?.let {
            return it.writeImage(image, formatName, imagePath)
        }

        if (!ImageIO.write(image, formatName, imagePath.toFile())) {
            val convertedImage =
                BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

            val g2d = convertedImage.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()

            return ImageIO.write(convertedImage, formatName, imagePath.toFile())
        } else {
            return true
        }
    }
}

interface ImageWriter {

    fun writeImage(
        image: BufferedImage,
        formatName: String,
        imagePath: Path,
    ): Boolean
}
