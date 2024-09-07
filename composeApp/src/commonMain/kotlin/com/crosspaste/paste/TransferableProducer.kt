package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteItem

interface TransferableProducer {

    fun produce(
        pasteItem: PasteItem,
        localOnly: Boolean,
        filterFile: Boolean,
    ): PasteTransferable?

    fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        filterFile: Boolean,
        primary: Boolean,
    ): PasteTransferable?
}
