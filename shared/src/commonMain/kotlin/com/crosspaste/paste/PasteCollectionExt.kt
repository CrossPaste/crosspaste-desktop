package com.crosspaste.paste

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.bindItem
import com.crosspaste.paste.item.clear
import com.crosspaste.path.UserDataPathProvider

fun PasteCollection.clear(
    clearResource: Boolean = true,
    pasteCoordinate: PasteCoordinate,
    userDataPathProvider: UserDataPathProvider,
) {
    pasteItems.forEach {
        it.clear(clearResource, pasteCoordinate, userDataPathProvider)
    }
}

fun PasteCollection.bindItems(
    pasteCoordinate: PasteCoordinate,
    syncToDownload: Boolean = false,
): PasteCollection =
    PasteCollection(
        pasteItems.map {
            it.bindItem(pasteCoordinate, syncToDownload)
        },
    )
