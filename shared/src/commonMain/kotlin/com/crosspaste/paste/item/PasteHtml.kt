package com.crosspaste.paste.item

interface PasteHtml {

    val html: String

    fun getText(): String

    fun getBackgroundColor(): Int
}
