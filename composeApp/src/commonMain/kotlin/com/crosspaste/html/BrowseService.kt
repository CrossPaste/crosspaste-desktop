package com.crosspaste.html

interface BrowseService {

    fun html2Image(html: String): ByteArray?

    fun quit()
}
