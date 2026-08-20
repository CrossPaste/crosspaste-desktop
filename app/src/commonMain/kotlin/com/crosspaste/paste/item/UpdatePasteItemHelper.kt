package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.copy
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import kotlinx.serialization.json.put

class UpdatePasteItemHelper(
    val pasteDao: PasteDao,
    val pasteItemReader: PasteItemReader,
    val searchContentService: SearchContentService,
) {
    suspend fun updateColor(
        pasteData: PasteData,
        newColor: Long,
        colorPasteItem: ColorPasteItem,
    ): Result<ColorPasteItem> {
        val newPasteItem =
            createColorPasteItem(
                identifiers = colorPasteItem.identifiers,
                color = newColor.toInt(),
                extraInfo = colorPasteItem.extraInfo,
            )
        return updateIfUnchanged(
            pasteData = pasteData,
            pasteItem = newPasteItem,
            pasteSearchContent =
                searchContentService.createSearchContent(
                    pasteData.source,
                    pasteItemReader.getSearchContent(newPasteItem),
                ),
        )
    }

    suspend fun updateHtml(
        pasteData: PasteData,
        newHtml: String,
        backgroundColor: Int? = null,
        htmlPasteItem: HtmlPasteItem,
    ): Result<HtmlPasteItem> {
        var newPasteItem = htmlPasteItem.copy(newHtml)

        if (backgroundColor != null) {
            newPasteItem =
                newPasteItem.copy {
                    put(PasteItemProperties.BACKGROUND, backgroundColor)
                } as HtmlPasteItem
        }

        return updateIfUnchanged(
            pasteData = pasteData,
            pasteItem = newPasteItem,
            pasteSearchContent =
                searchContentService.createSearchContent(
                    pasteData.source,
                    pasteItemReader.getSearchContent(newPasteItem),
                ),
            addedSize = newPasteItem.size - htmlPasteItem.size,
        )
    }

    suspend fun updateText(
        pasteData: PasteData,
        newText: String,
        textPasteItem: TextPasteItem,
    ): Result<TextPasteItem> {
        val newPasteItem = textPasteItem.copy(newText)
        return updateIfUnchanged(
            pasteData = pasteData,
            pasteItem = newPasteItem,
            pasteSearchContent =
                searchContentService.createSearchContent(
                    pasteData.source,
                    pasteItemReader.getSearchContent(newPasteItem),
                ),
            addedSize = newPasteItem.size - textPasteItem.size,
        )
    }

    suspend fun updateTitle(
        pasteData: PasteData,
        title: String,
        urlPasteItem: UrlPasteItem,
    ): Result<UrlPasteItem> {
        val newUrlPasteItem =
            urlPasteItem.copy {
                put(PasteItemProperties.TITLE, title)
            } as UrlPasteItem

        // This runs as an async writeback after a network fetch. Compare the
        // complete old item so neither URL edits nor same-hash metadata edits
        // can be overwritten by the late title.
        val applied =
            pasteDao.updatePasteAppearItemIfUnchanged(
                expectedPasteData = pasteData,
                pasteItem = newUrlPasteItem,
                pasteSearchContent =
                    searchContentService.createSearchContent(
                        pasteData.source,
                        listOfNotNull(
                            title,
                            pasteItemReader.getSearchContent(newUrlPasteItem),
                        ),
                    ),
                addedSize = newUrlPasteItem.size - urlPasteItem.size,
            )
        return if (applied) {
            Result.success(newUrlPasteItem)
        } else {
            Result.failure(
                IllegalStateException(
                    "Paste ${pasteData.id} changed since the title was fetched; title not applied.",
                ),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : PasteItem> updateName(
        pasteData: PasteData,
        name: String,
        pasteItem: T,
    ): Result<T> {
        val oldNameSize =
            pasteItem
                .getUserEditName()
                ?.encodeToByteArray()
                ?.size
                ?.toLong() ?: 0L
        val newPasteItem: T =
            pasteItem.copy {
                put(PasteItemProperties.NAME, name)
            } as T

        return updateIfUnchanged(
            pasteData = pasteData,
            pasteItem = newPasteItem,
            pasteSearchContent =
                searchContentService.createSearchContent(
                    pasteData.source,
                    listOfNotNull(
                        name,
                        pasteItemReader.getSearchContent(newPasteItem),
                    ),
                ),
            addedSize = name.encodeToByteArray().size.toLong() - oldNameSize,
        )
    }

    private suspend fun <T : PasteItem> updateIfUnchanged(
        pasteData: PasteData,
        pasteItem: T,
        pasteSearchContent: String,
        addedSize: Long = 0L,
    ): Result<T> =
        if (
            pasteDao.updatePasteAppearItemIfUnchanged(
                expectedPasteData = pasteData,
                pasteItem = pasteItem,
                pasteSearchContent = pasteSearchContent,
                addedSize = addedSize,
            )
        ) {
            Result.success(pasteItem)
        } else {
            Result.failure(
                IllegalStateException(
                    "Paste ${pasteData.id} changed while it was being edited.",
                ),
            )
        }
}
