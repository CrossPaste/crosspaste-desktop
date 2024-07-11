package com.crosspaste.paste

interface PasteDataFlavor

interface PasteTransferable {

    fun getTransferData(pasteDataFlavor: PasteDataFlavor): Any
}
