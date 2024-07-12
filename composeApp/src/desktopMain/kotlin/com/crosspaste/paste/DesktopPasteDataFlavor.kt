package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor

data class DesktopPasteDataFlavor(val dataFlavor: DataFlavor) : PasteDataFlavor {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DesktopPasteDataFlavor) return false

        if (dataFlavor != other.dataFlavor) return false

        return true
    }

    override fun hashCode(): Int {
        return dataFlavor.hashCode()
    }
}

fun DataFlavor.toPasteDataFlavor(): PasteDataFlavor {
    return DesktopPasteDataFlavor(this)
}
