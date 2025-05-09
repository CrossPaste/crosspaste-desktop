package com.crosspaste.image

import okio.Path
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object DesktopImageWriter : ImageWriter<BufferedImage> {

    private val specialFormatWriteMap: Map<String, ImageWriter<BufferedImage>> =
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

        return if (!ImageIO.write(image, formatName, imagePath.toFile())) {
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
}
