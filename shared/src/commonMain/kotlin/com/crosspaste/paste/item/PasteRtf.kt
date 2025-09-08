package com.crosspaste.paste.item

interface PasteRtf : PasteCoordinateBinder {

    val rtf: String

    fun getText(): String

    fun getHtml(): String

    fun getBackgroundColor(): Int
}
