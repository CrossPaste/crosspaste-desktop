package com.crosspaste.image

import java.nio.file.Path

interface ImageWriter<Image> {

    fun writeImage(
        image: Image,
        formatName: String,
        imagePath: Path,
    ): Boolean
}
