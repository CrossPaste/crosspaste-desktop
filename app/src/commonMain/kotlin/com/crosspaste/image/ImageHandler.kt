package com.crosspaste.image

import androidx.compose.ui.unit.IntSize
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.Source
import okio.Path

interface ImageHandler<Image> : ImageWriter<Image> {

    suspend fun readImage(imagePath: Path): Image?

    suspend fun readImage(source: Source): Image?

    suspend fun readImage(byteReadChannel: ByteReadChannel): Image?

    suspend fun readSize(imagePath: Path): IntSize?
}
