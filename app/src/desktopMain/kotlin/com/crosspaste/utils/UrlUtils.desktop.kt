package com.crosspaste.utils

import java.net.URL

actual fun getUrlUtils(): UrlUtils {
    return DesktopUrlUtils
}

object DesktopUrlUtils : UrlUtils {
    override fun isValidUrl(string: String): Boolean {
        return runCatching {
            URL(string)
            true
        }.getOrElse { false }
    }
}
