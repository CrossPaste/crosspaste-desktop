package com.clipevery.utils

import java.util.Base64

object HtmlUtils {

    fun dataUrl(html: String): String {
        val encodedContent = Base64.getEncoder().encodeToString(html.toByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }
}