package com.crosspaste.utils

import java.io.ByteArrayInputStream
import javax.swing.text.Document
import javax.swing.text.rtf.RTFEditorKit

actual fun getRtfUtils(): RtfUtils {
    return DesktopRtfUtils
}

object DesktopRtfUtils : RtfUtils {

    private val rtfParser = RTFEditorKit()

    override fun getRtfText(rtf: String): String {
        val document: Document = rtfParser.createDefaultDocument()
        rtfParser.read(ByteArrayInputStream(rtf.encodeToByteArray()), document, 0)
        return document.getText(0, document.length)
    }
}
