package com.crosspaste.paste.item

interface PasteRtf : PasteCoordinateBinder {

    val rtf: String

    fun getBackgroundColor(): Int
}
