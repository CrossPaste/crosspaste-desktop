package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteItem

interface TransferableProducer {

    fun produce(
        pasteItem: PasteItem,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    ): PasteTransferable?

    fun produce(
        pasteData: PasteData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
        primary: Boolean = false,
    ): PasteTransferable?
}
