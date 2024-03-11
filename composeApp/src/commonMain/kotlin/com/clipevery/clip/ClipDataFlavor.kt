package com.clipevery.clip

import java.awt.datatransfer.DataFlavor

class ClipDataFlavor(private val value: Any): DataFlavor() {

    fun getData(): Any {
        return value
    }
}