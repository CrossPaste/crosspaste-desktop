package com.crosspaste.paste

import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.HtmlPasteItem.Companion.HTML2IMAGE
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.RtfPasteItem.Companion.RTF2IMAGE
import com.crosspaste.utils.getFileUtils

/**
 * Sets the relativePath on HtmlPasteItem and RtfPasteItem before serialization for sync.
 * This was previously done inside PasteDataSerializer but is now extracted so that
 * the core module's serializer can remain platform-independent.
 */
fun PasteData.prepareForSync() {
    val fileUtils = getFileUtils()
    val pasteCoordinate = getPasteCoordinate()

    fun setRelativePath(
        pasteItem: PasteItem,
        coord: PasteCoordinate,
    ) {
        when (pasteItem) {
            is HtmlPasteItem -> {
                pasteItem.relativePath =
                    fileUtils.createPasteRelativePath(
                        pasteCoordinate = coord,
                        fileName = HTML2IMAGE,
                    )
            }
            is RtfPasteItem -> {
                pasteItem.relativePath =
                    fileUtils.createPasteRelativePath(
                        pasteCoordinate = coord,
                        fileName = RTF2IMAGE,
                    )
            }
            else -> {}
        }
    }

    pasteAppearItem?.let { setRelativePath(it, pasteCoordinate) }
    pasteCollection.pasteItems.forEach { setRelativePath(it, pasteCoordinate) }
}
