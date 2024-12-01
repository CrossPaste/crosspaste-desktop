package com.crosspaste.paste

import com.crosspaste.paste.item.PasteItem
import com.crosspaste.realm.paste.PasteData

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
