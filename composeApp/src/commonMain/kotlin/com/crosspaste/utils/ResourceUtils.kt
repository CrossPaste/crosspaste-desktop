package com.crosspaste.utils

expect fun getResourceUtils(): ResourceUtils

interface ResourceUtils {

    fun readResourceBytes(fileName: String): ByteArray
}
