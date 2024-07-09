package com.crosspaste.utils

object HtmlUtils {

    private val codecsUtils = getCodecsUtils()

    fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.toByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }
}
