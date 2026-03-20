package com.crosspaste.paste.item

import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.getRtfUtils

class DefaultPasteItemReader : PasteItemReader {

    private val htmlUtils by lazy { getHtmlUtils() }
    private val rtfUtils by lazy { getRtfUtils() }

    override fun getText(pasteItem: PasteItem): String =
        when (pasteItem) {
            is HtmlPasteItem -> htmlUtils.getHtmlText(pasteItem.html) ?: ""
            is RtfPasteItem -> rtfUtils.getText(pasteItem.rtf) ?: ""
            is TextPasteItem -> pasteItem.text
            is UrlPasteItem -> pasteItem.url
            is ColorPasteItem -> pasteItem.toHexString()
            is FilesPasteItem -> pasteItem.relativePathList.joinToString(", ") { it.substringAfterLast("/") }
            is ImagesPasteItem -> pasteItem.relativePathList.joinToString(", ") { it.substringAfterLast("/") }
        }

    override fun getSearchContent(pasteItem: PasteItem): String? =
        when (pasteItem) {
            is HtmlPasteItem -> htmlUtils.getHtmlText(pasteItem.html)?.lowercase()
            is RtfPasteItem -> rtfUtils.getText(pasteItem.rtf)?.lowercase()
            is TextPasteItem -> pasteItem.text.lowercase()
            is UrlPasteItem -> pasteItem.url.lowercase()
            is ColorPasteItem -> pasteItem.toHexString()
            is FilesPasteItem -> pasteItem.fileInfoTreeMap.keys.joinToString(" ") { it.lowercase() }
            is ImagesPasteItem -> pasteItem.fileInfoTreeMap.keys.joinToString(" ") { it.lowercase() }
        }

    override fun getSummary(pasteItem: PasteItem): String = getText(pasteItem)

    override fun getPreviewHtml(pasteItem: PasteItem): String? =
        when (pasteItem) {
            is HtmlPasteItem -> htmlUtils.truncateForPreview(pasteItem.html)
            is RtfPasteItem -> rtfUtils.rtfToHtml(pasteItem.rtf)?.let { htmlUtils.truncateForPreview(it) }
            else -> null
        }
}
