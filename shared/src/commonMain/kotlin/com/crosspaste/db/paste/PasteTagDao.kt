package com.crosspaste.db.paste

import com.crosspaste.paste.PasteTag

interface PasteTagDao : QueryPasteTag {

    suspend fun getMaxSortOrder(): Long

    suspend fun createPasteTag(
        name: String,
        color: Long,
    ): Long

    suspend fun updatePasteTagName(
        id: Long,
        name: String,
    )

    suspend fun updatePasteTagColor(
        id: Long,
        color: Long,
    )

    suspend fun updatePasteTagsSortOrder(orderedIds: List<Long>)

    fun switchPinPasteTagBlock(
        pasteDataId: Long,
        pasteTagId: Long,
    )

    fun getPasteTagsBlock(pasteDataId: Long): List<Long>

    suspend fun addTagsToPastes(
        pasteDataIds: List<Long>,
        pasteTagIds: Set<Long>,
    )

    suspend fun countTagsForPastes(pasteDataIds: List<Long>): Map<Long, Int>

    fun deletePasteTagBlock(id: Long)

    fun getAllTagsBlock(): List<PasteTag>
}
