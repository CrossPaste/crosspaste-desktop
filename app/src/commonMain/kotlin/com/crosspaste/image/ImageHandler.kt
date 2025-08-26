package com.crosspaste.image

import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.Source
import okio.Path

interface ImageHandler<Image> : ImageWriter<Image> {

    fun readImage(imagePath: Path): Image?

    fun readImage(source: Source): Image?

    fun readImage(byteReadChannel: ByteReadChannel): Image?
}
