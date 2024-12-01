package com.crosspaste.dto.paste

import kotlinx.serialization.Serializable

@Serializable
data class SyncPasteLabel(
    val id: String,
    val color: Int,
    val text: String,
)
