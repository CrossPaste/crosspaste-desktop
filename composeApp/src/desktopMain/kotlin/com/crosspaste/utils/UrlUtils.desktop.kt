package com.crosspaste.utils

import java.net.MalformedURLException
import java.net.URL

actual fun getUrlUtils(): UrlUtils {
    return DesktopUrlUtils
}

object DesktopUrlUtils : UrlUtils {
    override fun isValidUrl(string: String): Boolean {
        return try {
            URL(string)
            true
        } catch (_: MalformedURLException) {
            false
        }
    }
}
