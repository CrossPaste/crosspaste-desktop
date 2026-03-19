package com.crosspaste.paste

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.clear
import com.crosspaste.path.UserDataPathProvider

fun PasteData.clear(userDataPathProvider: UserDataPathProvider) {
    val pasteCoordinate = PasteCoordinate(id, appInstanceId, createTime)
    pasteAppearItem?.clear(
        pasteCoordinate = pasteCoordinate,
        userDataPathProvider = userDataPathProvider,
    )
    pasteCollection.clear(
        pasteCoordinate = pasteCoordinate,
        userDataPathProvider = userDataPathProvider,
    )
}
