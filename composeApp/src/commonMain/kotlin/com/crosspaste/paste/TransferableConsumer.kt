package com.crosspaste.paste

interface TransferableConsumer {

    suspend fun consume(
        pasteTransferable: PasteTransferable,
        source: String?,
        remote: Boolean,
    )
}
