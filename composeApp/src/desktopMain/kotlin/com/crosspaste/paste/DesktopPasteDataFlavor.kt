package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor

data class DesktopPasteDataFlavor(val dataFlavor: DataFlavor) : PasteDataFlavor

fun DataFlavor.toPasteDataFlavor(): PasteDataFlavor {
    return DesktopPasteDataFlavor(this)
}
