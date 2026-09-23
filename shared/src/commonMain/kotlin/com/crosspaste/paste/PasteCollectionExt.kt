package com.crosspaste.paste

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.bindItem
import com.crosspaste.paste.item.clear
import com.crosspaste.path.UserDataPathProvider

fun PasteCollection.clear(
    clearResource: Boolean = true,
    userDataPathProvider: UserDataPathProvider,
) {
    pasteItems.forEach {
        it.clear(clearResource, userDataPathProvider)
    }
}

fun PasteCollection.bindItems(
    pasteCoordinate: PasteCoordinate,
    destinationPath: String? = null,
): PasteCollection =
    PasteCollection(
        pasteItems.map {
            it.bindItem(pasteCoordinate, destinationPath)
        },
    )
