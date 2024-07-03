package com.crosspaste.dto.pull

import kotlinx.serialization.Serializable

@Serializable
data class PullFileRequest(val appInstanceId: String, val clipId: Long, val chunkIndex: Int) {

    override fun toString(): String {
        return "PullFileRequest(appInstanceId='$appInstanceId', clipId=$clipId, chunkIndex=$chunkIndex)"
    }
}
