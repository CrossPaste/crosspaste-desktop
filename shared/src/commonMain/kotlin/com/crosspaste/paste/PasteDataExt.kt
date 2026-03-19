package com.crosspaste.paste

import com.crosspaste.paste.item.clear
import com.crosspaste.path.UserDataPathProvider

fun PasteData.clear(userDataPathProvider: UserDataPathProvider) {
    pasteAppearItem?.clear(
        userDataPathProvider = userDataPathProvider,
    )
    pasteCollection.clear(
        userDataPathProvider = userDataPathProvider,
    )
}
