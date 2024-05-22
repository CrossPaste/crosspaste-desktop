package com.clipevery.ui.base

import java.nio.file.Path

interface UISupport {

    fun openUrlInBrowser(url: String)

    fun openEmailClient(email: String)

    fun openHtml(html: String)

    fun browseFile(filePath: Path)

    fun openImage(imagePath: Path)

    fun openText(text: String)
}
