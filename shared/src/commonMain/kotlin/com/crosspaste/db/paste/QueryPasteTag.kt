package com.crosspaste.db.paste

import com.crosspaste.paste.PasteTag
import kotlinx.coroutines.flow.Flow

interface QueryPasteTag {

    fun getAllTagsFlow(): Flow<List<PasteTag>>
}
