package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.clientapi.ClientApiResult
import com.clipevery.net.clientapi.FailureResult
import com.clipevery.net.clientapi.SendClipClientApi
import com.clipevery.net.clientapi.createFailureResult
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.SyncExtraInfo
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
import com.clipevery.utils.buildUrl
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.encodeToString

class SyncClipTaskExecutor(
    private val clipDao: ClipDao,
    private val sendClipClientApi: SendClipClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    override val taskType: Int = TaskType.SYNC_CLIP_TASK

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val syncExtraInfo: SyncExtraInfo = TaskUtils.getExtraInfo(clipTask, SyncExtraInfo::class)
        val mapResult =
            clipDao.getClipData(clipTask.clipDataId!!)?.let { clipData ->
                val deferredResults: MutableList<Deferred<Pair<String, ClientApiResult>>> = mutableListOf()
                for (entryHandler in syncManager.getSyncHandlers()) {
                    if (entryHandler.value.syncRuntimeInfo.allowSend) {
                        val deferred =
                            ioScope.async {
                                try {
                                    val clientHandler = entryHandler.value
                                    val port = clientHandler.syncRuntimeInfo.port
                                    val targetAppInstanceId = clientHandler.syncRuntimeInfo.appInstanceId
                                    clientHandler.getConnectHostAddress()?.let {
                                        val syncClipResult =
                                            sendClipClientApi.sendClip(clipData, targetAppInstanceId) { urlBuilder ->
                                                buildUrl(urlBuilder, it, port)
                                            }
                                        return@async Pair(entryHandler.key, syncClipResult)
                                    } ?: run {
                                        return@async Pair(
                                            entryHandler.key,
                                            createFailureResult(
                                                StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                                                "Failed to get connect host address by ${entryHandler.key}",
                                            ),
                                        )
                                    }
                                } catch (e: Exception) {
                                    return@async Pair(
                                        entryHandler.key,
                                        createFailureResult(
                                            StandardErrorCode.SYNC_CLIP_ERROR,
                                            "Failed to sync clip to ${entryHandler.key}",
                                        ),
                                    )
                                }
                            }
                        deferredResults.add(deferred)
                    }
                }

                deferredResults.associate { it.await() }
            } ?: run {
                mapOf()
            }

        val fails: Map<String, FailureResult> =
            mapResult
                .filter { it.value is FailureResult }
                .mapValues { it.value as FailureResult }

        syncExtraInfo.syncFails.clear()

        if (fails.isEmpty()) {
            return SuccessClipTaskResult(DesktopJsonUtils.JSON.encodeToString(syncExtraInfo))
        } else {
            syncExtraInfo.syncFails.addAll(fails.keys)
            return createFailureClipTaskResult(
                logger = logger,
                retryHandler = { syncExtraInfo.executionHistories.size < 3 },
                startTime = clipTask.modifyTime,
                fails = fails.values,
                extraInfo = syncExtraInfo,
            )
        }
    }
}
