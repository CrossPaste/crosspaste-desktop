package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.util.Properties

object ResourceUtils {

    fun loadImageBitmap(resourcePath: String): ImageBitmap {
        // Assuming `resourcePath` is a valid path for an image file within your resources directory.
        val image = org.jetbrains.skia.Image.makeFromEncoded(
            Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
                ?.readBytes()!!)
        return image.toComposeImageBitmap()
    }

    fun loadProperties(fileName: String): Properties {
        val properties = Properties()
        val classLoader = Thread.currentThread().contextClassLoader
        classLoader.getResourceAsStream(fileName).use { inputStream ->
            if (inputStream == null) {
                throw IllegalArgumentException("File not found: $fileName")
            } else {
                properties.load(inputStream)
            }
        }
        return properties
    }
}