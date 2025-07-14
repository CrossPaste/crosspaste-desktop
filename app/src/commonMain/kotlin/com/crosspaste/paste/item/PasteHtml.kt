package com.crosspaste.paste.item

import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.getHtmlUtils

interface PasteHtml {

    val html: String

    fun getText(): String = getHtmlUtils().getHtmlText(html)

    fun getBackgroundColor(): Color?
}
