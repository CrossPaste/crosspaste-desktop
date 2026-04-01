package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PastePullService(
    private val pasteDao: PasteDao,
    private val pasteboardService: PasteboardService,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) {

    private val logger = KotlinLogging.logger {}

    private val deviceMaxCreateTime: MutableMap<String, Long> = ConcurrentMap()
    private val mutex = Mutex()

    suspend fun init() {
        val maxCreateTimeMap = pasteDao.getMaxCreateTimeByRemoteAppInstanceId()
        deviceMaxCreateTime.putAll(maxCreateTimeMap)
        maxCreateTimeMap.forEach { (appInstanceId, maxCreateTime) ->
            logger.debug { "Initialized sync state for $appInstanceId: maxCreateTime=$maxCreateTime" }
        }
        logger.info { "PastePullService initialized with ${deviceMaxCreateTime.size} device(s)" }
    }

    fun getMaxCreateTime(appInstanceId: String): Long? = deviceMaxCreateTime[appInstanceId]

    suspend fun updateMaxCreateTime(
        appInstanceId: String,
        createTime: Long,
    ) {
        mutex.withLock {
            val current = deviceMaxCreateTime[appInstanceId]
            if (current == null || createTime > current) {
                deviceMaxCreateTime[appInstanceId] = createTime
            }
        }
    }

    fun removeDevice(appInstanceId: String) {
        deviceMaxCreateTime.remove(appInstanceId)
        logger.debug { "Removed sync state for $appInstanceId" }
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

        pasteboardService.tryWriteRemotePasteboardList(allPasteData)

        logger.info { "Pulled and wrote ${allPasteData.size} paste(s) from all devices" }
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
