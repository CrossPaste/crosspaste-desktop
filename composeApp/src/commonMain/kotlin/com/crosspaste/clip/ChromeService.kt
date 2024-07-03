package com.crosspaste.clip

interface ChromeService {

    fun html2Image(html: String): ByteArray?

    fun quit()
}
