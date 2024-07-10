package com.crosspaste.paste.item

import okio.Path
import org.jsoup.Jsoup

interface PasteHtml : PasteInit {

    var html: String

    fun getText(): String {
        return Jsoup.parse(html).text()
    }

    fun getHtmlImagePath(): Path
}
