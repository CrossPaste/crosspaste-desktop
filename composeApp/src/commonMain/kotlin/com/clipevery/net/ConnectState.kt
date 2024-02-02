package com.clipevery.net

import com.clipevery.dao.sync.SyncRuntimeInfo

interface ConnectState {

    suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun next(): Boolean
}
