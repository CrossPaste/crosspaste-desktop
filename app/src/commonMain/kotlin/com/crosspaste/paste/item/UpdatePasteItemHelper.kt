package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.copy
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import kotlinx.serialization.json.put

class UpdatePasteItemHelper(
    val pasteDao: PasteDao,
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
        return pasteDao
            .updatePasteAppearItem(
                id = pasteData.id,
                pasteItem = newPasteItem,
                pasteSearchContent =
                    searchContentService.createSearchContent(
                        pasteData.source,
                        newPasteItem.getSearchContent(),
                    ),
            ).map {
                newPasteItem
            }
    }

    suspend fun updateText(
        pasteData: PasteData,
        newText: String,
        textPasteItem: TextPasteItem,
    ): Result<TextPasteItem> {
        val newPasteItem = textPasteItem.copy(newText)
        return pasteDao
            .updatePasteAppearItem(
                id = pasteData.id,
                pasteItem = newPasteItem,
                pasteSearchContent =
                    searchContentService.createSearchContent(
                        pasteData.source,
                        newPasteItem.getSearchContent(),
                    ),
                addedSize = newPasteItem.size - textPasteItem.size,
            ).map {
                newPasteItem
            }
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

        return pasteDao
            .updatePasteAppearItem(
                id = pasteData.id,
                pasteItem = newUrlPasteItem,
                pasteSearchContent =
                    searchContentService.createSearchContent(
                        pasteData.source,
                        listOf(
                            title,
                            newUrlPasteItem.getSearchContent(),
                        ),
                    ),
                addedSize = title.length.toLong(),
            ).map {
                newUrlPasteItem
            }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : PasteItem> updateName(
        pasteData: PasteData,
        name: String,
        pasteItem: T,
    ): Result<T> {
        val newPasteItem: T =
            pasteItem.copy {
                put(PasteItemProperties.NAME, name)
            } as T

        return pasteDao
            .updatePasteAppearItem(
                id = pasteData.id,
                pasteItem = newPasteItem,
                pasteSearchContent =
                    searchContentService.createSearchContent(
                        pasteData.source,
                        listOf(
                            name,
                            newPasteItem.getSearchContent(),
                        ).mapNotNull { it },
                    ),
                addedSize = name.length.toLong(),
            ).map {
                newPasteItem
            }
    }
}
