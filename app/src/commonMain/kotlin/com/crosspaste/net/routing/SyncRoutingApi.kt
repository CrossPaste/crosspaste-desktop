package com.crosspaste.net.routing

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.sync.SyncHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
        val logger = KotlinLogging.logger {}
        // Ensure that all notifications are completed before exiting, with a timeout
        runBlocking {
            runCatching {
                withTimeout(5000) {
                    getSyncHandlers()
                        .values
                        .map { syncHandler ->
                            async { syncHandler.notifyExit() }
                        }.awaitAll()
                }
            }.onFailure { e ->
                logger.warn(e) { "notifyExit timed out or failed" }
            }
        }
    }

    fun getSyncHandlers(): Map<String, SyncHandler>

    fun getSyncHandler(appInstanceId: String): SyncHandler? = getSyncHandlers()[appInstanceId]

    fun getSyncPlatform(appInstanceId: String): Platform? = getSyncHandler(appInstanceId)?.getSyncPlatform()

    fun updateSyncInfo(syncInfo: SyncInfo)

    fun trustSyncInfo(
        syncInfo: SyncInfo,
        host: String?,
    )

    fun removeSyncHandler(appInstanceId: String)
}
