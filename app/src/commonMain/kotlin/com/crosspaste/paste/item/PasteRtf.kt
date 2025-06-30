package com.crosspaste.paste.item

import com.crosspaste.utils.getRtfUtils

interface PasteRtf : PasteCoordinateBinder {

    val rtf: String

    fun getText(): String {
        return getRtfUtils().getRtfText(rtf)
    }
}
