package com.crosspaste.utils

import java.net.URI

actual fun getUrlUtils(): UrlUtils = DesktopUrlUtils

object DesktopUrlUtils : UrlUtils {
    override fun isValidUrl(string: String): Boolean =
        runCatching {
            URI(string).toURL()
            true
        }.getOrElse { false }

    override fun isHttpUrl(string: String): Boolean =
        runCatching {
            val url = URI(string).toURL()
            (url.protocol == "http" || url.protocol == "https") && url.host.isNotBlank()
        }.getOrElse { false }
}
