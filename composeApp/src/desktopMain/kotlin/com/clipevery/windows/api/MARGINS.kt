package com.clipevery.windows.api

import com.sun.jna.Structure

class MARGINS : Structure() {
    var cxLeftWidth = 0
    var cxRightWidth = 0
    var cyTopHeight = 0
    var cyBottomHeight = 0
    override fun getFieldOrder(): List<String> {
        return mutableListOf("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
    }
}
