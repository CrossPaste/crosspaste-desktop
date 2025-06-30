package com.crosspaste.image

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import okio.Path
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object DesktopImageWriter : ImageWriter<BufferedImage> {

    private val fileUtils = getFileUtils()

    private val specialFormatWriteMap: Map<String, ImageWriter<BufferedImage>> =
        mapOf(
            "webp" to WebpImageWriter(),
        )

    override fun writeImage(
        image: BufferedImage,
        formatName: String,
        imagePath: Path,
    ): Boolean {
        return specialFormatWriteMap[formatName]?.writeImage(image, formatName, imagePath) ?: run {
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
    }
}
