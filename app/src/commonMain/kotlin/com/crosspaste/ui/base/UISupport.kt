package com.crosspaste.ui.base

import com.crosspaste.paste.PasteData
import okio.Path

interface UISupport {

    fun openUrlInBrowser(url: String)

    fun getCrossPasteWebUrl(path: String = ""): String

    fun openCrossPasteWebInBrowser(path: String = "") {
        openUrlInBrowser(getCrossPasteWebUrl(path))
    }

    fun openEmailClient(email: String?)

    fun openHtml(
        id: Long,
        html: String,
    )

    fun browseFile(filePath: Path)

    fun openColorPicker(pasteData: PasteData)

    fun openImage(imagePath: Path)

    fun openText(pasteData: PasteData)

    fun openRtf(pasteData: PasteData)

    fun openPasteData(
        pasteData: PasteData,
        index: Int = 0,
    )

    fun jumpPrivacyAccessibility()
}
