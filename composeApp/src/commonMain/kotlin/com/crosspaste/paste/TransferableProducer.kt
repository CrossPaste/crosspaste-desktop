package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import java.awt.datatransfer.Transferable

interface TransferableProducer {

    fun produce(
        pasteData: PasteData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    ): Transferable?
}
