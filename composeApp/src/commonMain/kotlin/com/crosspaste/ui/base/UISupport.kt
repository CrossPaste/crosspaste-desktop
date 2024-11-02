package com.crosspaste.ui.base

import com.crosspaste.paste.item.PasteColor
import com.crosspaste.realm.paste.PasteData
import okio.Path
import org.mongodb.kbson.ObjectId

interface UISupport {

    fun openUrlInBrowser(url: String)

    fun openCrossPasteWebInBrowser(path: String = "")

    fun openEmailClient(email: String)

    fun openHtml(
        objectId: ObjectId,
        html: String,
    )

    fun browseFile(filePath: Path)

    fun openColorPicker(pasteColor: PasteColor)

    fun openImage(imagePath: Path)

    fun openText(pasteData: PasteData)

    fun openRtf(pasteData: PasteData)

    fun openPasteData(
        pasteData: PasteData,
        index: Int = 0,
    )

    fun jumpPrivacyAccessibility()
}
