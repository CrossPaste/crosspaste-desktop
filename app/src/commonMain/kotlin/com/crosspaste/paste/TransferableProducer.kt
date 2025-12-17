package com.crosspaste.paste

import com.crosspaste.paste.item.PasteItem

interface TransferableProducer {

    suspend fun produce(
        pasteItem: PasteItem,
        localOnly: Boolean,
    ): PasteTransferable?

    suspend fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        primary: Boolean,
    ): PasteTransferable?
}
