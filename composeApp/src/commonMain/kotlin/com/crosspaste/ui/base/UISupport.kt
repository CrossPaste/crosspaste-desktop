package com.crosspaste.ui.base

import com.crosspaste.dao.paste.PasteData
import okio.Path

interface UISupport {

    fun openUrlInBrowser(url: String)

    fun openEmailClient(email: String)

    fun openHtml(html: String)

    fun browseFile(filePath: Path)

    fun openImage(imagePath: Path)

    fun openText(text: String)

    fun openPasteData(pasteData: PasteData)
}
