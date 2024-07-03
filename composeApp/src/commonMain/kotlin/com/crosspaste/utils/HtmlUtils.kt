package com.crosspaste.utils

object HtmlUtils {

    private val encryptUtils = getEncryptUtils()

    fun dataUrl(html: String): String {
        val encodedContent = encryptUtils.base64Encode(html.toByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }
}
