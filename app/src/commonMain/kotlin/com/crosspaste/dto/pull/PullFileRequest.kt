package com.crosspaste.dto.pull

import kotlinx.serialization.Serializable

@Serializable
data class PullFileRequest(val appInstanceId: String, val pasteId: Long, val chunkIndex: Int) {

    override fun toString(): String {
        return "PullFileRequest(appInstanceId='$appInstanceId', pasteId=$pasteId, chunkIndex=$chunkIndex)"
    }
}
