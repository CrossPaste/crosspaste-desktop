package com.crosspaste.paste.item

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getRtfUtils
import okio.Path

interface PasteRtf : PasteCoordinateBinder {

    var rtf: String

    fun getText(): String {
        return getRtfUtils().getRtfText(rtf)
    }

    fun getRtfImagePath(userDataPathProvider: UserDataPathProvider): Path
}
