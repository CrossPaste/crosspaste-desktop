package com.crosspaste.utils

import org.jsoup.Jsoup

actual fun getHtmlUtils(): HtmlUtils {
    return DesktopHtmlUtils
}

object DesktopHtmlUtils : HtmlUtils {
    private val codecsUtils = getCodecsUtils()

    override fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.toByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }

    override fun getHtmlText(html: String): String {
        return Jsoup.parse(html).text()
    }
}
