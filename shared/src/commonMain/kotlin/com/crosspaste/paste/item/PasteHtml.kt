package com.crosspaste.paste.item

import com.crosspaste.utils.getHtmlUtils

interface PasteHtml {

    val html: String

    fun getText(): String = getHtmlUtils().getHtmlText(html)

    fun getBackgroundColor(): Int?
}
