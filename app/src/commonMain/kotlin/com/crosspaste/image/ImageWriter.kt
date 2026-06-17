package com.crosspaste.image

import okio.Path

interface ImageWriter<Image> {

    suspend fun writeImage(
        image: Image,
        formatName: String,
        imagePath: Path,
    ): Boolean
}
