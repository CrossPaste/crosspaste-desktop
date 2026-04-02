package com.crosspaste.db.paste

import com.crosspaste.paste.PasteData
import kotlinx.coroutines.flow.Flow

interface SearchPasteData {

    /**
     * Search paste data with optional type filtering.
     *
     * [pasteTypeList] accepts multiple paste types for OR-matching (SQL `IN` clause),
     * allowing mobile clients to query several types in a single request.
     * An empty list means no type filter (returns all types).
     */
    suspend fun searchPasteData(
        searchTerms: List<String>,
        local: Boolean? = null,
        pasteTypeList: List<Int> = listOf(),
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): List<PasteData>

    fun searchPasteDataFlow(
        searchTerms: List<String>,
        local: Boolean? = null,
        pasteTypeList: List<Int> = listOf(),
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): Flow<List<PasteData>>

    suspend fun searchBySource(source: String): List<PasteData>
}
