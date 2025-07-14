package com.crosspaste.paste.item

import kotlin.math.min

interface PasteText {

    val text: String

    fun previewText(): String = text.substring(0, min(256, text.length))
}
