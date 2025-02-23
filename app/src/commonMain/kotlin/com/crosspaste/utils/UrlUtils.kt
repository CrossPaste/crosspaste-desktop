package com.crosspaste.utils

expect fun getUrlUtils(): UrlUtils

interface UrlUtils {

    fun isValidUrl(string: String): Boolean
}
