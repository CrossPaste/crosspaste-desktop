package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData

interface TransferableProducer {

    fun produce(
        pasteData: PasteData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    ): PasteTransferable?
}
