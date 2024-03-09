package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.net.clientapi.SyncClipClientApi
import com.clipevery.net.clientapi.SyncClipResult
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.SyncExtraInfo
import com.clipevery.utils.JsonUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.buildUrl
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream

class SyncClipTaskExecutor(private val lazyClipDao: Lazy<ClipDao>,
                           private val syncClipClientApi: SyncClipClientApi,
                           private val syncManager: SyncManager): SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val clipDao: ClipDao by lazy { lazyClipDao.value }

    private val ioScope = CoroutineScope(ioDispatcher)

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val syncExtraInfo: SyncExtraInfo = TaskUtils.getExtraInfo(clipTask, SyncExtraInfo::class)
        val mapResult = clipDao.getClipData(clipTask.clipId)?.let { clipData ->
            val outputStream = ByteArrayOutputStream()
            JsonUtils.JSON.encodeToStream(clipData, outputStream)
            val clipTaskBytes = outputStream.toByteArray()

            val deferredResults: MutableList<Deferred<Pair<String, Int>>> = mutableListOf()
            for (entryHandler in syncManager.getSyncHandlers()) {
                val deferred = ioScope.async {
                    val clientHandler = entryHandler.value
                    var syncClipResult = SyncClipResult.FAILED
                    clientHandler.getConnectHostAddress()?.let {
                        syncClipResult = syncClipClientApi.syncClip(clipData, clipTaskBytes) {
                            urlBuilder -> buildUrl(urlBuilder, it, port, "sync", "clip")
                        }
                    }
                    return@async Pair(entryHandler.key, syncClipResult)
                }
                deferredResults.add(deferred)
            }

            deferredResults.associate { it.await() }
        } ?: run {
            mapOf()
        }

        val fails = mapResult.filter { it.value == SyncClipResult.FAILED }.map { it.key }

        syncExtraInfo.syncFails.clear()

        if (fails.isEmpty()) {
            return SuccessClipTaskResult(syncExtraInfo)
        } else {
            syncExtraInfo.syncFails.addAll(fails)
            return FailClipTaskResult(syncExtraInfo)
        }
    }

    override fun needRetry(clipTask: ClipTask, newExtraInfo: ClipTaskExtraInfo): Boolean {
        return newExtraInfo.executionHistories.size < 3
    }

}