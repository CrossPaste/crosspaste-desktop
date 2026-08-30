package com.crosspaste.ui.base

import com.crosspaste.app.CrossPasteWebService
import com.crosspaste.paste.PasteData
import okio.Path

interface UISupport {

    val crossPasteWebService: CrossPasteWebService

    /**
     * Must never throw: the URL may come from pasteboard data (malformed,
     * whitespace-padded) or the device may have no handler for it.
     * Implementations normalize the URL, catch open failures, and notify
     * the user instead of propagating.
     */
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

    fun openFile(filePath: Path)

    fun openText(pasteData: PasteData)

    fun openRtf(pasteData: PasteData)

    fun openPasteData(
        pasteData: PasteData,
        index: Int = 0,
    )

    fun jumpPrivacyAccessibility()
}
