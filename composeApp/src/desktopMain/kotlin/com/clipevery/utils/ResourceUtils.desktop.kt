package com.clipevery.utils

import java.util.Properties

actual fun getResourceUtils(): ResourceUtils {
    return DesktopResourceUtils
}

object DesktopResourceUtils : ResourceUtils {

    override fun loadProperties(fileName: String): Properties {
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
