package com.crosspaste.paste.item

import com.crosspaste.utils.getRtfUtils

interface PasteRtf : PasteCoordinateBinder {

    val rtf: String

    fun getText(): String = getRtfUtils().getRtfText(rtf)
}
