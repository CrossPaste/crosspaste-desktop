package com.crosspaste.db.paste

import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem

data class PasteResourceInfo(
    val pasteCount: Long,
    val pasteSize: Long,
    val textCount: Long,
    val textSize: Long,
    val colorCount: Long,
    val colorSize: Long,
    val urlCount: Long,
    val urlSize: Long,
    val htmlCount: Long,
    val htmlSize: Long,
    val rtfCount: Long,
    val rtfSize: Long,
    val imageCount: Long,
    val imageSize: Long,
    val fileCount: Long,
    val fileSize: Long,
)

class PasteResourceInfoBuilder {
    private var pasteCount: Long = 0
    private var pasteSize: Long = 0
    private var textCount: Long = 0
    private var textSize: Long = 0
    private var colorCount: Long = 0
    private var colorSize: Long = 0
    private var urlCount: Long = 0
    private var urlSize: Long = 0
    private var htmlCount: Long = 0
    private var htmlSize: Long = 0
    private var rtfCount: Long = 0
    private var rtfSize: Long = 0
    private var imageCount: Long = 0
    private var imageSize: Long = 0
    private var fileCount: Long = 0
    private var fileSize: Long = 0

    fun add(pasteItem: PasteItem) {
        pasteCount++
        pasteSize += pasteItem.size
        when (pasteItem) {
            is TextPasteItem -> {
                textCount++
                textSize += pasteItem.size
            }
            is ColorPasteItem -> {
                colorCount++
                colorSize += pasteItem.size
            }
            is UrlPasteItem -> {
                urlCount++
                urlSize += pasteItem.size
            }
            is HtmlPasteItem -> {
                htmlCount++
                htmlSize += pasteItem.size
            }
            is RtfPasteItem -> {
                rtfCount++
                rtfSize += pasteItem.size
            }
            is ImagesPasteItem -> {
                imageCount++
                imageSize += pasteItem.size
            }
            is FilesPasteItem -> {
                fileCount++
                fileSize += pasteItem.size
            }
        }
    }

    fun build(): PasteResourceInfo {
        return PasteResourceInfo(
            pasteCount,
            pasteSize,
            textCount,
            textSize,
            colorCount,
            colorSize,
            urlCount,
            urlSize,
            htmlCount,
            htmlSize,
            rtfCount,
            rtfSize,
            imageCount,
            imageSize,
            fileCount,
            fileSize,
        )
    }
}