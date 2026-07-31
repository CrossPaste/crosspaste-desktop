package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.utils.StripedMutex
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PastePullCursorManager(
    private val pasteDao: PasteDao,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {

    private val logger = KotlinLogging.logger {}

    private val deviceMaxCreateTime: MutableMap<String, Long> = ConcurrentMap()
    private val deviceMutex = StripedMutex()
    private val initMutex = Mutex()

    suspend fun init() {
        initMutex.withLock {
            val storedPasteMaxCreateTimes = pasteDao.getMaxCreateTimeByRemoteAppInstanceId()
            val persistedPullCursorMaxCreateTimes =
                pasteDao.getPastePullCursorMaxCreateTimes()
            val appInstanceIds =
                storedPasteMaxCreateTimes.keys + persistedPullCursorMaxCreateTimes.keys
            val maxCreateTimeMap =
                appInstanceIds.associateWith { appInstanceId ->
                    listOfNotNull(
                        storedPasteMaxCreateTimes[appInstanceId],
                        persistedPullCursorMaxCreateTimes[appInstanceId],
                    ).max()
                }

            deviceMaxCreateTime.putAll(maxCreateTimeMap)
            maxCreateTimeMap.forEach { (appInstanceId, maxCreateTime) ->
                logger.debug { "Initialized sync state for $appInstanceId: maxCreateTime=$maxCreateTime" }
            }
            logger.info { "Paste pull cursor initialized with ${deviceMaxCreateTime.size} device(s)" }
        }
    }

    fun getMaxCreateTime(appInstanceId: String): Long? = deviceMaxCreateTime[appInstanceId]

    suspend fun updateMaxCreateTime(
        appInstanceId: String,
        createTime: Long,
    ) {
        deviceMutex.withLock(appInstanceId) {
            updateMaxCreateTimeUnsafe(appInstanceId, createTime)
        }
    }

    suspend fun persistDiscardedMaxCreateTime(
        appInstanceId: String,
        createTime: Long,
    ): Boolean =
        deviceMutex.withLock(appInstanceId) {
            if (syncRuntimeInfoDao.getSyncRuntimeInfo(appInstanceId) == null) {
                logger.debug { "Skip persisting paste pull cursor for removed device $appInstanceId" }
                return@withLock false
            }

            pasteDao.upsertPastePullCursorMaxCreateTime(
                appInstanceId = appInstanceId,
                maxCreateTime = createTime,
            )
            updateMaxCreateTimeUnsafe(appInstanceId, createTime)
            true
        }

    suspend fun removeDevice(appInstanceId: String) {
        deviceMutex.withLock(appInstanceId) {
            pasteDao.deletePastePullCursor(appInstanceId)
            val storedPasteMaxCreateTime =
                pasteDao.getMaxCreateTimeByRemoteAppInstanceId()[appInstanceId]
            if (storedPasteMaxCreateTime == null) {
                deviceMaxCreateTime.remove(appInstanceId)
            } else {
                deviceMaxCreateTime[appInstanceId] = storedPasteMaxCreateTime
            }
            logger.debug { "Removed paste pull cursor for $appInstanceId" }
        }
    }

    private fun updateMaxCreateTimeUnsafe(
        appInstanceId: String,
        createTime: Long,
    ) {
        val current = deviceMaxCreateTime[appInstanceId]
        if (current == null || createTime > current) {
            deviceMaxCreateTime[appInstanceId] = createTime
        }
    }
}
