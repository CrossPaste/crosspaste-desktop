package com.crosspaste.utils

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

object DesktopResourceUtils {

    fun loadProperties(fileName: String): Properties =
        requireNotNull(loadPropertiesOrNull(fileName)) { "Resource $fileName not found" }

    /**
     * Like [loadProperties], but returns empty properties when the resource is
     * absent. Release jars exclude development.properties, so DevConfig must be
     * able to start without it.
     */
    fun loadOptionalProperties(fileName: String): Properties = loadPropertiesOrNull(fileName) ?: Properties()

    fun readResourceBytes(fileName: String): ByteArray =
        ClassLoaderResourceLoader.load(fileName).use { stream ->
            stream.readBytes()
        }

    private fun loadPropertiesOrNull(fileName: String): Properties? =
        ClassLoaderResourceLoader.openOrNull(fileName)?.use { inputStream ->
            Properties().apply {
                InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                    load(reader)
                }
            }
        }
}

private object ClassLoaderResourceLoader {

    // Conveyor/JNI, Gradle tests and IDEs all set the context class loader; the
    // class's own loader keeps resource loading working on threads that do not.
    private fun classLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: DesktopResourceUtils::class.java.classLoader

    fun openOrNull(resourcePath: String): InputStream? = classLoader().getResourceAsStream(resourcePath)

    fun load(resourcePath: String): InputStream =
        requireNotNull(openOrNull(resourcePath)) { "Resource $resourcePath not found" }
}
