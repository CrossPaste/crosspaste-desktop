package com.crosspaste.paste

import com.crosspaste.realm.paste.PasteData

interface PasteMenuService {

    fun copyPasteData(pasteData: PasteData)

    fun openPasteData(
        pasteData: PasteData,
        index: Int = 0,
    )

    fun deletePasteData(pasteData: PasteData)
}
