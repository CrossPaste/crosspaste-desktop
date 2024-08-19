package com.crosspaste.html

interface ChromeService {

    var startSuccess: Boolean

    fun html2Image(html: String): ByteArray?

    fun quit()
}
