package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.copy
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.getUrlUtils
import kotlinx.serialization.json.JsonObject

/**
 * Edits a paste's CONTENT in place — as opposed to [item.UpdatePasteItemHelper],
 * which adjusts a single item's metadata. A content edit must keep every
 * stored clipboard flavor consistent: the appear item is rebuilt with the new
 * content, plain-text companion items in the collection are re-derived from
 * it, and other text-like companions (whose markup cannot be derived
 * faithfully) are dropped rather than left stale — a missing flavor degrades
 * gracefully, a stale one pastes the OLD content into plain-text targets.
 *
 * The write is a single old-value CAS statement ([PasteDao.updatePasteContent]),
 * so a concurrent edit, an in-app change, or a deletion surfaces as
 * [EditOutcome.Conflict] instead of a silent last-writer-wins overwrite.
 */
class PasteContentEditor(
    private val pasteDao: PasteDao,
    private val pasteItemReader: PasteItemReader,
    private val searchContentService: SearchContentService,
) {

    sealed interface EditOutcome {
        /**
         * [urlChanged] tells the caller a URL paste now points elsewhere, so
         * desktop-side Open Graph residue (preview image file, rendering
         * task) must be cleaned up and re-rendered.
         */
        data class Updated(
            val newItem: PasteItem,
            val urlChanged: Boolean,
        ) : EditOutcome

        /** The row changed, was deleted, or is not LOADED; re-read and retry. */
        data object Conflict : EditOutcome

        /** The paste's type has no text representation to edit. */
        data object NotEditable : EditOutcome

        /** The content does not parse for this type (color hex, URL). */
        data class InvalidContent(
            val reason: String,
        ) : EditOutcome
    }

    /**
     * [expectedHash] is the hash the editor saw when it read the paste. The
     * DAO also compares the complete server-side snapshot in [pasteData].
     */
    suspend fun updateContent(
        pasteData: PasteData,
        newContent: String,
        expectedHash: String,
    ): EditOutcome {
        val mainItem = pasteData.pasteAppearItem ?: return EditOutcome.NotEditable
        val newMainItem =
            when (mainItem) {
                is TextPasteItem -> mainItem.copy(newContent)
                is HtmlPasteItem -> mainItem.copy(newContent)
                is RtfPasteItem -> mainItem.copy(newContent)
                is UrlPasteItem -> {
                    if (!getUrlUtils().isValidUrl(newContent)) {
                        return EditOutcome.InvalidContent(
                            "'$newContent' is not a valid URL.",
                        )
                    }
                    rebuildUrlItem(mainItem, newContent)
                }

                is ColorPasteItem -> {
                    val color =
                        parseColorHex(newContent) ?: return EditOutcome.InvalidContent(
                            "Invalid color '${newContent.trim()}'; expected #RRGGBB or #RRGGBBAA.",
                        )
                    createColorPasteItem(
                        identifiers = mainItem.identifiers,
                        color = color.toInt(),
                        extraInfo = mainItem.extraInfo,
                    )
                }

                else -> return EditOutcome.NotEditable
            }

        val oldCompanions = pasteData.pasteCollection.pasteItems
        val derivedText = pasteItemReader.getText(newMainItem)
        val newCompanions =
            oldCompanions.mapNotNull { item ->
                when (item) {
                    // The plain-text flavor is always derivable from the new
                    // main item. Match the release pipeline by dropping it
                    // when rich content has no valid plain-text representation.
                    is TextPasteItem -> derivedText.takeIf { it.isNotEmpty() }?.let { item.copy(it) }
                    is HtmlPasteItem, is RtfPasteItem, is UrlPasteItem, is ColorPasteItem -> null
                    else -> item
                }
            }
        val addedSize =
            (newMainItem.size - mainItem.size) +
                (newCompanions.sumOf { it.size } - oldCompanions.sumOf { it.size })
        val searchContent =
            searchContentService.createSearchContent(
                pasteData.source,
                listOfNotNull(
                    (newMainItem as? UrlPasteItem)?.getTitle(),
                    pasteItemReader.getSearchContent(newMainItem),
                ),
            )
        val applied =
            pasteDao.updatePasteContent(
                expectedPasteData = pasteData,
                pasteItem = newMainItem,
                pasteCollection = PasteCollection(newCompanions),
                pasteSearchContent = searchContent,
                addedSize = addedSize,
                expectedHash = expectedHash,
            )
        if (!applied) return EditOutcome.Conflict
        return EditOutcome.Updated(
            newItem = newMainItem,
            urlChanged = mainItem is UrlPasteItem && mainItem.url != newContent,
        )
    }

    /**
     * The stored title and marketing preview path describe the OLD page;
     * carrying them onto a changed URL would show (and index) stale metadata
     * next to the new link, and would let the Open Graph renderer keep
     * targeting the old preview location.
     */
    private fun rebuildUrlItem(
        urlPasteItem: UrlPasteItem,
        newUrl: String,
    ): PasteItem {
        val extraInfo =
            urlPasteItem.extraInfo
                ?.let { info ->
                    if (newUrl == urlPasteItem.url) {
                        info
                    } else {
                        JsonObject(
                            info.filterKeys {
                                it != PasteItemProperties.TITLE && it != PasteItemProperties.MARKETING_PATH
                            },
                        )
                    }
                }?.takeIf { it.isNotEmpty() }
        return createUrlPasteItem(
            identifiers = urlPasteItem.identifiers,
            url = newUrl,
            extraInfo = extraInfo,
        )
    }

    companion object {
        /**
         * Parses the color notation the CLI shows (`PasteColor.toHexString`:
         * #RRGGBBAA, alpha last) plus the plain #RRGGBB shorthand (alpha = FF).
         */
        fun parseColorHex(value: String): Long? {
            val hex = value.trim().removePrefix("#")
            if (hex.length != 6 && hex.length != 8) return null
            if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
            val red = hex.substring(0, 2).toLong(16)
            val green = hex.substring(2, 4).toLong(16)
            val blue = hex.substring(4, 6).toLong(16)
            val alpha = if (hex.length == 8) hex.substring(6, 8).toLong(16) else 0xFFL
            return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
}
