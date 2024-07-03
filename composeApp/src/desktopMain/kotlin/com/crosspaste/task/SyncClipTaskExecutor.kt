package com.crosspaste.task

import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.task.ClipTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SendClipClientApi
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.extra.SyncExtraInfo
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.TaskUtils.createFailureClipTaskResult
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.ioDispatcher
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
