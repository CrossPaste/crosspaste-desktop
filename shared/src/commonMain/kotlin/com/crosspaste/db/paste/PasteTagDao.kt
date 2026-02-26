package com.crosspaste.db.paste

import com.crosspaste.paste.PasteTag
import kotlinx.coroutines.flow.Flow

interface PasteTagDao {

    fun getAllTagsFlow(): Flow<List<PasteTag>>

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

    fun switchPinPasteTagBlock(
        pasteDataId: Long,
        pasteTagId: Long,
    )

    fun getPasteTagsBlock(pasteDataId: Long): List<Long>

    fun deletePasteTagBlock(id: Long)
}
