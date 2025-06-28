package com.crosspaste.paste.item

import androidx.compose.ui.graphics.Color
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getHtmlUtils
import okio.Path

interface PasteHtml {

    val html: String

    val basePath: String?

    val relativePath: String

    fun getText(): String {
        return getHtmlUtils().getHtmlText(html)
    }

    fun getBackgroundColor(): Color?

    fun getHtmlImagePath(userDataPathProvider: UserDataPathProvider): Path
}
