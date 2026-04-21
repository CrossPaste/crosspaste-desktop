package com.crosspaste.dto.pull

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * WebSocket file pull request — two modes expressed as a sealed hierarchy.
 *
 * **Chunk mode** (Desktop ↔ Desktop): [ChunkRequest] with `id` + `chunkIndex`.
 * **Whole-file mode** (Desktop ↔ Chrome extension): [WholeFileRequest] with `fileName`
 *   plus `id` (Desktop-side lookup) or `hash` (Chrome-side lookup).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("mode")
sealed class WsPullFileRequest {

    @Serializable
    @SerialName("chunk")
    data class ChunkRequest(
        val id: Long,
        val chunkIndex: Int,
    ) : WsPullFileRequest()

    @Serializable
    @SerialName("whole")
    data class WholeFileRequest(
        val id: Long = 0,
        val hash: String = "",
        val fileName: String,
    ) : WsPullFileRequest()
}
