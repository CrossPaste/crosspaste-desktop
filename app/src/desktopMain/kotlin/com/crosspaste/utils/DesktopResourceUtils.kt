package com.crosspaste.utils

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

object DesktopResourceUtils {

    fun loadProperties(fileName: String): Properties {
        val properties = Properties()
        val inputStream = ClassLoaderResourceLoader.load(fileName)
        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { inputStreamReader ->
            properties.load(inputStreamReader)
        }
        return properties
    }

    fun loadOptionalProperties(fileName: String): Properties =
        if (ClassLoaderResourceLoader.exists(fileName)) loadProperties(fileName) else Properties()

    fun readResourceBytes(fileName: String): ByteArray {
        ClassLoaderResourceLoader.load(fileName).use { stream ->
            return stream.readBytes()
        }
    }
}

private object ClassLoaderResourceLoader {
    fun exists(resourcePath: String): Boolean =
        Thread.currentThread().contextClassLoader?.getResource(resourcePath) != null ||
            this::class.java.getResource(resourcePath) != null

    fun load(resourcePath: String): InputStream {
        val contextClassLoader = Thread.currentThread().contextClassLoader!!
        val resource =
            contextClassLoader.getResourceAsStream(resourcePath)
                ?: this::class.java.getResourceAsStream(resourcePath)
        return requireNotNull(resource) { "Resource $resourcePath not found" }
    }
}
