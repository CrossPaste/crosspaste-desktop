package com.crosspaste.dto.pull

import kotlinx.serialization.Serializable

/**
 * WebSocket file pull request supporting two modes:
 *
 * **Chunk mode** (pulling from Desktop): `id` + `chunkIndex` ≥ 0
 * Uses the same chunk system as HTTP pull — each chunk ≤ 1MB.
 *
 * **Whole-file mode** (pulling from Chrome extension): `hash` + `fileName`
 * Requests an entire file by paste hash and name. Chrome files are always ≤ 1MB.
 */
@Serializable
data class WsPullFileRequest(
    val id: Long = 0,
    val chunkIndex: Int = -1,
    val hash: String = "",
    val fileName: String = "",
) {
    fun isChunkMode(): Boolean = chunkIndex >= 0

    override fun toString(): String =
        if (isChunkMode()) {
            "WsPullFileRequest(chunk: id=$id, chunkIndex=$chunkIndex)"
        } else {
            "WsPullFileRequest(file: hash=$hash, fileName=$fileName)"
        }
}
