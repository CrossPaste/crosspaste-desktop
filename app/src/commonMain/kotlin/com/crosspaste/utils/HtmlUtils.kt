package com.crosspaste.utils

expect fun getHtmlUtils(): HtmlUtils

interface HtmlUtils {

    fun dataUrl(html: String): String

    fun getHtmlText(html: String): String
}
