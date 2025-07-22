package com.crosspaste.net.routing

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.sync.SyncHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface SyncRoutingApi {

    val realTimeSyncScope: CoroutineScope

    fun markExit(appInstanceId: String) {
        getSyncHandler(appInstanceId)?.let { syncHandler ->
            realTimeSyncScope.launch(CoroutineName("MarkExit")) {
                syncHandler.markExit()
            }
        }
    }

    fun notifyExit() {
        getSyncHandlers().values.forEach { syncHandler ->
            // Ensure that the notification is completed before exiting
            runBlocking { syncHandler.notifyExit() }
        }
    }

    fun getSyncHandlers(): Map<String, SyncHandler>

    fun getSyncHandler(appInstanceId: String): SyncHandler? = getSyncHandlers()[appInstanceId]

    fun getSyncPlatform(appInstanceId: String): Platform? = getSyncHandler(appInstanceId)?.getSyncPlatform()

    fun updateSyncInfo(
        syncInfo: SyncInfo,
        refresh: Boolean,
    )

    fun removeSyncHandler(appInstanceId: String)
}
