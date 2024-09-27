package com.crosspaste.image

import okio.Path
import org.jetbrains.skia.Bitmap

interface ImageCreator {

    fun createBitmap(path: Path): Bitmap

    fun createBitmap(
        path: Path,
        width: Int,
        height: Int,
    ): Bitmap
}
