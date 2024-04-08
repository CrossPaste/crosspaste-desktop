package com.clipevery.clip

interface ChromeService {

    fun html2Image(html: String): ByteArray?
}
