package com.crosspaste.dto.paste

import com.crosspaste.paste.item.PasteItem
import kotlinx.serialization.Serializable

@Serializable
data class SyncPasteData(
    val id: String,
    val appInstanceId: String,
    val pasteId: Long,
    val pasteType: Int,
    val source: String?,
    val size: Long,
    val hash: String,
    val favorite: Boolean,
    val pasteAppearItem: PasteItem?,
    val pasteCollection: SyncPasteCollection?,
    val labels: Set<SyncPasteLabel>,
)
