package com.clipevery.clip.item

import org.jsoup.Jsoup
import java.nio.file.Path

interface ClipHtml : ClipInit {

    var html: String

    fun getText(): String {
        return Jsoup.parse(html).text()
    }

    fun getHtmlImagePath(): Path
}
