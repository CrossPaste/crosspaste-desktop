package com.crosspaste.paste

import com.crosspaste.paste.item.PasteItemReader

class PasteDataHelper(
    private val pasteItemReader: PasteItemReader,
) {

    fun getSummary(
        pasteData: PasteData,
        loading: String,
        unknown: String,
    ): String =
        if (pasteData.pasteState == PasteState.LOADING) {
            loading
        } else {
            pasteData.pasteAppearItem?.let {
                pasteItemReader.getText(it).ifEmpty { null }
            } ?: unknown
        }
}
