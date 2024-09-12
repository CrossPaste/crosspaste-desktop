package com.crosspaste.paste.item

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getHtmlUtils
import okio.Path

interface PasteHtml : PasteCoordinateBinder {

    var html: String

    fun getText(): String {
        return getHtmlUtils().getHtmlText(html)
    }

    fun getHtmlImagePath(userDataPathProvider: UserDataPathProvider): Path
}
