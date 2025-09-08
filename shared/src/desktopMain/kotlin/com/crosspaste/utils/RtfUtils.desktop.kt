package com.crosspaste.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.html.MinimalHTMLWriter
import javax.swing.text.rtf.RTFEditorKit

actual fun getRtfUtils(): RtfUtils = DesktopRtfUtils

object DesktopRtfUtils : RtfUtils {

    private val logger = KotlinLogging.logger {}

    private val rtfParser = RTFEditorKit()

    override fun getText(rtf: String): String? =
        runCatching {
            val doc = DefaultStyledDocument()
            rtfParser.read(ByteArrayInputStream(rtf.encodeToByteArray()), doc, 0)
            doc.getText(0, doc.length)
        }.onFailure { e ->
            logger.error(e) { "Failed to parse RTF text" }
        }.getOrNull()

    override fun rtfToHtml(rtf: String): String? {
        return runCatching {
            val doc = DefaultStyledDocument()
            StringReader(rtf).use { reader ->
                RTFEditorKit().read(reader, doc, 0)
            }
            val out = StringWriter()
            MinimalHTMLWriter(out, doc).write()
            return out.toString()
        }.onFailure { e ->
            logger.error(e) { "Failed to convert RTF to HTML" }
        }.getOrNull()
    }
}
