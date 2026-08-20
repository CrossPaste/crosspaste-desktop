package com.crosspaste.paste

import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.item.clear
import com.crosspaste.paste.item.clearRenderingFiles
import com.crosspaste.path.UserDataPathProvider

fun PasteData.clear(userDataPathProvider: UserDataPathProvider) {
    (pasteAppearItem as? UrlPasteItem)?.clearRenderingFiles(
        pasteCoordinate = getPasteCoordinate(),
        userDataPathProvider = userDataPathProvider,
    )
    pasteAppearItem?.clear(
        userDataPathProvider = userDataPathProvider,
    )
    pasteCollection.clear(
        userDataPathProvider = userDataPathProvider,
    )
}
