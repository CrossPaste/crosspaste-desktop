package com.clipevery.ui.resource

import java.io.File
import java.io.InputStream

object DesktopAbsoluteClipResourceLoader: ClipResourceLoader {

    override fun load(resourcePath: String): InputStream {
        return File(resourcePath).inputStream()
    }
}
