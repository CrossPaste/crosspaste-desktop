package com.crosspaste.paste

import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteItem

interface TransferableProducer {

    fun produce(
        pasteItem: PasteItem,
        localOnly: Boolean,
    ): PasteTransferable?

    fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        primary: Boolean,
    ): PasteTransferable?
}
