package com.crosspaste.dto.push

object PushHeaders {
    const val SYNC_MODE: String = "X-Sync-Mode"
    const val SYNC_MODE_PUSH: String = "push"

    const val PASTE_ID: String = "X-Paste-Id"
    const val CHUNK_INDEX: String = "X-Chunk-Index"
    const val SESSION_TOKEN: String = "X-Session-Token"
}
