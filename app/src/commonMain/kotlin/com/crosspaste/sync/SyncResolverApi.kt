package com.crosspaste.sync

interface SyncResolverApi {
    suspend fun emitEvent(event: SyncEvent)
}
