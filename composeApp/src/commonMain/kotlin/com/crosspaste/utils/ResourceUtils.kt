package com.crosspaste.utils

import java.io.InputStream
import java.util.Properties

expect fun getResourceUtils(): ResourceUtils

interface ResourceUtils {

    fun resourceInputStream(fileName: String): InputStream

    fun loadProperties(fileName: String): Properties
}
