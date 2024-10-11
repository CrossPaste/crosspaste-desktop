package com.crosspaste.ui.base

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

    fun openImage(imagePath: Path)

    fun openText(
        objectId: ObjectId,
        text: String,
    )

    fun openPasteData(
        pasteData: PasteData,
        index: Int = 0,
    )

    fun jumpPrivacyAccessibility()
}
