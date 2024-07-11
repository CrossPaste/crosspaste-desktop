package com.crosspaste.paste

import java.awt.datatransfer.Transferable

data class DesktopReadTransferable(val transferable: Transferable) : PasteTransferable {
    override fun getTransferData(pasteDataFlavor: PasteDataFlavor): Any {
        return transferable.getTransferData((pasteDataFlavor as DesktopPasteDataFlavor).dataFlavor)
    }
}
