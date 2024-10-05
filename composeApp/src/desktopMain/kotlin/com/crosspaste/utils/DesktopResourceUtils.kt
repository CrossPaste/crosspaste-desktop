package com.crosspaste.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.ResourceLoader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

@OptIn(ExperimentalComposeUiApi::class)
object DesktopResourceUtils {

    private val loader = ResourceLoader.Default

    fun resourceInputStream(fileName: String): InputStream {
        return loader.load(fileName)
    }

    fun loadProperties(fileName: String): Properties {
        val properties = Properties()
        InputStreamReader(resourceInputStream(fileName), StandardCharsets.UTF_8).use { inputStreamReader ->
            properties.load(inputStreamReader)
        }
        return properties
    }

    fun readResourceBytes(fileName: String): ByteArray {
        loader.load(fileName).use { stream ->
            return stream.readBytes()
        }
    }
}
