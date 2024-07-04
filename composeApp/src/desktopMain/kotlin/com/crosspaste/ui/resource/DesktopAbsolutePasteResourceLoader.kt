package com.crosspaste.ui.resource

import java.io.File
import java.io.InputStream

object DesktopAbsolutePasteResourceLoader : PasteResourceLoader {

    override fun load(resourcePath: String): InputStream {
        return File(resourcePath).inputStream()
    }
}
