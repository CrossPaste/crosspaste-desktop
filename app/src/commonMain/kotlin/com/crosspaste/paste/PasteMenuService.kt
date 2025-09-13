package com.crosspaste.paste

import androidx.navigation.NavController

interface PasteMenuService {

    fun copyPasteData(pasteData: PasteData)

    fun openPasteData(
        navController: NavController,
        pasteData: PasteData,
        index: Int = 0,
    )

    fun deletePasteData(pasteData: PasteData)

    fun quickPasteFromMainWindow(pasteData: PasteData)

    fun quickPasteFromSearchWindow(pasteData: PasteData)
}
