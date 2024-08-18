package com.crosspaste.html

interface BrowseService {

    var startSuccess: Boolean

    fun html2Image(html: String): ByteArray?

    fun quit()
}
