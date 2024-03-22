package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.net.clientapi.SendClipClientApi
import com.clipevery.net.clientapi.SyncClipResult
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.SyncExtraInfo
import com.clipevery.utils.JsonUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.buildUrl
import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class SyncClipTaskExecutor(private val lazyClipDao: Lazy<ClipDao>,
                           private val sendClipClientApi: SendClipClientApi,
                           private val syncManager: SyncManager): SingleTypeTaskExecutor {

    private val clipDao: ClipDao by lazy { lazyClipDao.value }

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val syncExtraInfo: SyncExtraInfo = TaskUtils.getExtraInfo(clipTask, SyncExtraInfo::class)
        val mapResult = clipDao.getClipData(clipTask.clipId)?.let { clipData ->
            val deferredResults: MutableList<Deferred<Pair<String, Int>>> = mutableListOf()
            for (entryHandler in syncManager.getSyncHandlers()) {
                val deferred = ioScope.async {
                    val clientHandler = entryHandler.value
                    var syncClipResult = SyncClipResult.FAILED
                    val port = clientHandler.syncRuntimeInfo.port
                    clientHandler.getConnectHostAddress()?.let {
                        syncClipResult = sendClipClientApi.sendClip(clipData) {
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
            return SuccessClipTaskResult(JsonUtils.JSON.encodeToString(SyncExtraInfo.serializer(), syncExtraInfo))
        } else {
            syncExtraInfo.syncFails.addAll(fails)
            val needRetry = syncExtraInfo.executionHistories.size < 3
            return FailClipTaskResult(JsonUtils.JSON.encodeToString(SyncExtraInfo.serializer(), syncExtraInfo), needRetry)
        }
    }
}