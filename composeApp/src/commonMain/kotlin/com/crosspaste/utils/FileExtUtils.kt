package com.crosspaste.utils

object FileExtUtils {

    private val canPreviewImageMap =
        setOf(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "tiff", "svg",
        )

    fun canPreviewImage(ext: String): Boolean = canPreviewImageMap.contains(ext.lowercase())
}
