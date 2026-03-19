package com.crosspaste.dto.paste

import com.crosspaste.paste.item.PasteItem
import kotlinx.serialization.Serializable

@Serializable
data class SyncPasteCollection(
    val pasteItems: List<PasteItem>,
)
