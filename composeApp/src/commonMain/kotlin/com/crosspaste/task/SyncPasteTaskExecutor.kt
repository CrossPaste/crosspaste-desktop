package com.crosspaste.task

import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.task.PasteTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SendPasteClientApi
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.extra.SyncExtraInfo
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.getTaskUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.encodeToString

class SyncPasteTaskExecutor(
    private val pasteDao: PasteDao,
    private val sendPasteClientApi: SendPasteClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    private val taskUtils = getTaskUtils()

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    override val taskType: Int = TaskType.SYNC_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val syncExtraInfo: SyncExtraInfo = taskUtils.getExtraInfo(pasteTask, SyncExtraInfo::class)
        val mapResult =
            pasteDao.getPasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
                val deferredResults: MutableList<Deferred<Pair<String, ClientApiResult>>> = mutableListOf()
                for (entryHandler in syncManager.getSyncHandlers()) {
                    if (entryHandler.value.syncRuntimeInfo.allowSend && entryHandler.value.compatibility) {
                        val deferred =
                            ioScope.async {
                                try {
                                    val clientHandler = entryHandler.value
                                    val port = clientHandler.syncRuntimeInfo.port
                                    val targetAppInstanceId = clientHandler.syncRuntimeInfo.appInstanceId
                                    clientHandler.getConnectHostAddress()?.let {
                                        val syncPasteResult =
                                            sendPasteClientApi.sendPaste(pasteData, targetAppInstanceId) { urlBuilder ->
                                                buildUrl(urlBuilder, it, port)
                                            }
                                        return@async Pair(entryHandler.key, syncPasteResult)
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
                                            StandardErrorCode.SYNC_PASTE_ERROR,
                                            "Failed to sync paste to ${entryHandler.key}",
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
            return SuccessPasteTaskResult(jsonUtils.JSON.encodeToString(syncExtraInfo))
        } else {
            syncExtraInfo.syncFails.addAll(fails.keys)
            return taskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { syncExtraInfo.executionHistories.size < 3 },
                startTime = pasteTask.modifyTime,
                fails = fails.values,
                extraInfo = syncExtraInfo,
            )
        }
    }
}
