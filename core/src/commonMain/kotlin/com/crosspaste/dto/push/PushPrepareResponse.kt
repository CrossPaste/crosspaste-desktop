package com.crosspaste.dto.push

import kotlinx.serialization.Serializable

@Serializable
data class PushPrepareResponse(
    val pasteId: Long,
    val chunkCount: Int,
    val chunkSize: Long,
    val sessionToken: String,
    /**
     * True when the desktop has a `source` for this paste but no cached icon
     * for the sender — mobile should follow up with `POST /sync/icon/push/{source}`.
     * Always false when `source` is null or the icon is already cached.
     */
    val needIcon: Boolean = false,
)
