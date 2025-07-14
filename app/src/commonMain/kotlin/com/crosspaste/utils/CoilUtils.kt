package com.crosspaste.utils

import coil3.Bitmap
import coil3.Image
import okio.Path

expect fun getCoilUtils(): CoilUtils

interface CoilUtils {

    fun createBitmap(path: Path): Bitmap

    fun createBitmap(
        path: Path,
        width: Int,
        height: Int,
    ): Bitmap

    fun createImage(path: Path): Image = asImage(createBitmap(path))

    fun createImage(
        path: Path,
        width: Int,
        height: Int,
    ): Image = asImage(createBitmap(path, width, height))

    fun asImage(
        bitmap: Bitmap,
        shareable: Boolean = true,
    ): Image
}
