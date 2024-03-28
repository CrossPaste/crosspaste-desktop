package com.clipevery.dto.pull

import kotlinx.serialization.Serializable

@Serializable
data class PullFileRequest(val appInstanceId: String, val clipId: Int, val chunkIndex: Int) {

    override fun toString(): String {
        return "PullFileRequest(appInstanceId='$appInstanceId', clipId=$clipId, chunkIndex=$chunkIndex)"
    }
}