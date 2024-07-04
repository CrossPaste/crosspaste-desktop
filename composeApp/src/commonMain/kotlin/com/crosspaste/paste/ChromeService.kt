package com.crosspaste.paste

interface ChromeService {

    fun html2Image(html: String): ByteArray?

    fun quit()
}
