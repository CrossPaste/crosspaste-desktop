package com.crosspaste.html

interface HtmlRenderingService {

    var startSuccess: Boolean

    fun html2Image(html: String): ByteArray?

    fun quit()
}
