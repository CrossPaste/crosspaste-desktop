package com.crosspaste.utils

import java.net.URI

actual fun getUrlUtils(): UrlUtils {
    return DesktopUrlUtils
}

object DesktopUrlUtils : UrlUtils {
    override fun isValidUrl(string: String): Boolean {
        return runCatching {
            URI(string).toURL()
            true
        }.getOrElse { false }
    }
}
