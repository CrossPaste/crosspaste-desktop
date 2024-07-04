package com.crosspaste.paste.item

import org.jsoup.Jsoup
import java.nio.file.Path

interface PasteHtml : PasteInit {

    var html: String

    fun getText(): String {
        return Jsoup.parse(html).text()
    }

    fun getHtmlImagePath(): Path
}
