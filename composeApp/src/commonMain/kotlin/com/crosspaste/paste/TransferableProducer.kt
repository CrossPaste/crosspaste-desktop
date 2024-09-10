package com.crosspaste.paste

import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem

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
