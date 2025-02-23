package com.crosspaste.image

import okio.Path

interface ImageWriter<Image> {

    fun writeImage(
        image: Image,
        formatName: String,
        imagePath: Path,
    ): Boolean
}
