package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.copy
import kotlinx.serialization.json.put

/**
 * Adjusts a single item's metadata (name, URL title). Content edits go through
 * [com.crosspaste.paste.PasteContentEditor], which keeps companion flavors in sync.
 */
class UpdatePasteItemHelper(
    val pasteDao: PasteDao,
    val pasteItemReader: PasteItemReader,
    val searchContentService: SearchContentService,
) {
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
