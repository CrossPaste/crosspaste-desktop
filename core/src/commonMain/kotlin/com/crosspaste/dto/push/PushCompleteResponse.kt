package com.crosspaste.dto.push

import kotlinx.serialization.Serializable

@Serializable
data class PushCompleteResponse(
    val missingChunks: List<Int>,
) {
    val isComplete: Boolean get() = missingChunks.isEmpty()
}
