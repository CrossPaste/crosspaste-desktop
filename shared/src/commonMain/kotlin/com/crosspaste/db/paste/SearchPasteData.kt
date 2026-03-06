package com.crosspaste.db.paste

import com.crosspaste.paste.PasteData
import kotlinx.coroutines.flow.Flow

interface SearchPasteData {

    suspend fun searchPasteData(
        searchTerms: List<String>,
        local: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): List<PasteData>

    fun searchPasteDataFlow(
        searchTerms: List<String>,
        local: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): Flow<List<PasteData>>

    suspend fun searchBySource(source: String): List<PasteData>
}
