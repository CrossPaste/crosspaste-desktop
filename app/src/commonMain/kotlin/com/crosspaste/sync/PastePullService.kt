package com.crosspaste.sync

import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class PastePullService(
    private val pastePullCursorManager: PastePullCursorManager,
    private val pasteboardService: PasteboardService,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun init() {
        pastePullCursorManager.init()
    }

    fun getMaxCreateTime(appInstanceId: String): Long? = pastePullCursorManager.getMaxCreateTime(appInstanceId)

    suspend fun updateMaxCreateTime(
        appInstanceId: String,
        createTime: Long,
    ) {
        pastePullCursorManager.updateMaxCreateTime(appInstanceId, createTime)
    }

    suspend fun pullAllDevices(limit: Long = 10L) {
        val allPasteData = mutableListOf<PasteData>()

        syncManager.getSyncHandlers().forEach { (appInstanceId, handler) ->
            val syncRuntimeInfo = handler.currentSyncRuntimeInfo
            if (syncRuntimeInfo.connectState != SyncState.CONNECTED) {
                return@forEach
            }
            if (!syncRuntimeInfo.allowReceive) {
                logger.debug { "Skip pull from $appInstanceId: allowReceive is false" }
                return@forEach
            }

            val pulled = pullBatch(appInstanceId, limit)
            allPasteData.addAll(pulled)
        }

        if (allPasteData.isEmpty()) {
            return
        }

        allPasteData.sortBy { it.createTime }

        pasteboardService
            .tryWriteRemotePasteboardList(allPasteData)
            .onSuccess {
                logger.info { "Pulled and wrote ${allPasteData.size} paste(s) from all devices" }
            }.onFailure { e ->
                logger.warn(e) { "Stopped writing pulled pastes after a release failure" }
            }
    }

    suspend fun pullBatch(
        appInstanceId: String,
        limit: Long = 10L,
    ): List<PasteData> {
        val handler =
            syncManager.getSyncHandler(appInstanceId) ?: run {
                logger.debug { "No sync handler for $appInstanceId" }
                return emptyList()
            }

        val hostAddress =
            handler.getConnectHostAddress() ?: run {
                logger.debug { "No connect address for $appInstanceId" }
                return emptyList()
            }

        val syncRuntimeInfo = handler.currentSyncRuntimeInfo
        val hostAndPort = HostAndPort(hostAddress, syncRuntimeInfo.port)
        val createTime = getMaxCreateTime(appInstanceId)

        val result =
            pullClientApi.pullPasteBatch(
                targetAppInstanceId = appInstanceId,
                createTime = createTime,
                limit = limit,
            ) {
                buildUrl(hostAndPort)
            }

        return if (result is SuccessResult) {
            val pasteDataList = result.getResult<List<PasteData>>()
            if (pasteDataList.isNotEmpty()) {
                val maxCreateTime = pasteDataList.maxOf { it.createTime }
                updateMaxCreateTime(appInstanceId, maxCreateTime)
                logger.debug {
                    "Pulled ${pasteDataList.size} paste(s) from $appInstanceId, maxCreateTime=$maxCreateTime"
                }
            }
            pasteDataList
        } else {
            logger.warn { "Failed to pull paste batch from $appInstanceId: $result" }
            emptyList()
        }
    }
}
