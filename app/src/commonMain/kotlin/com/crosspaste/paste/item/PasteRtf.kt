package com.crosspaste.paste.item

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getRtfUtils
import okio.Path

interface PasteRtf : PasteCoordinateBinder {

    val rtf: String

    val basePath: String?

    val relativePath: String

    fun getText(): String {
        return getRtfUtils().getRtfText(rtf)
    }

    fun getRtfImagePath(userDataPathProvider: UserDataPathProvider): Path
}
