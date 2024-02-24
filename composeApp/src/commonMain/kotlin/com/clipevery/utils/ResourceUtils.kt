package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

object ResourceUtils {

    fun loadImageBitmap(resourcePath: String): ImageBitmap {
        // Assuming `resourcePath` is a valid path for an image file within your resources directory.
        val image = org.jetbrains.skia.Image.makeFromEncoded(
            Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
                ?.readBytes()!!)
        return image.toComposeImageBitmap()
    }
}