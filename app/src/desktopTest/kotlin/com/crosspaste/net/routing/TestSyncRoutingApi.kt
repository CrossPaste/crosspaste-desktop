package com.crosspaste.net.routing

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.SyncHandler
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope

class TestSyncRoutingApi : SyncRoutingApi {

    var exitState = false

    var syncInfo: SyncInfo? = null

    val innerSyncHandlers = mutableMapOf<String, SyncHandler>()

    override val realTimeSyncScope: CoroutineScope = CoroutineScope(ioDispatcher)

    override fun getSyncHandlers(): Map<String, SyncHandler> {
        return innerSyncHandlers
    }

    override fun updateSyncInfo(syncInfo: SyncInfo) {
        this.syncInfo = syncInfo
    }

    override fun removeSyncHandler(id: String) {
        innerSyncHandlers.remove(id)
    }
}
