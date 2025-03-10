package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class SyncPasteTaskExecutor(
    private val appControl: AppControl,
    private val pasteDao: PasteDao,
    private val pasteClientApi: PasteClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    override val taskType: Int = TaskType.SYNC_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val syncExtraInfo: SyncExtraInfo = TaskUtils.getExtraInfo(pasteTask, SyncExtraInfo::class)
        val mapResult =
            pasteDao.getNoDeletePasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
                val deferredResults: MutableList<Deferred<Pair<String, ClientApiResult>>> = mutableListOf()
                for (entryHandler in syncManager.getSyncHandlers()) {
                    if (!entryHandler.value.syncRuntimeInfo.allowSend ||
                        entryHandler.value.versionRelation != VersionRelation.EQUAL_TO ||
                        (syncExtraInfo.syncFails.isNotEmpty() && !syncExtraInfo.syncFails.contains(entryHandler.key))
                    ) {
                        continue
                    }
                    val deferred =
                        ioScope.async {
                            try {
                                val clientHandler = entryHandler.value
                                val port = clientHandler.syncRuntimeInfo.port
                                val targetAppInstanceId = clientHandler.syncRuntimeInfo.appInstanceId
                                clientHandler.getConnectHostAddress()?.let {
                                    val syncPasteResult =
                                        if (appControl.isSendEnabled()) {
                                            pasteClientApi.sendPaste(
                                                pasteData,
                                                targetAppInstanceId,
                                            ) {
                                                buildUrl(it, port)
                                            }
                                        } else {
                                            createFailureResult(
                                                StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_APP,
                                                "Failed to send paste to ${entryHandler.key}",
                                            )
                                        }

                                    if (syncPasteResult is SuccessResult) {
                                        appControl.completeSendOperation()
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
                            } catch (_: Exception) {
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
            return TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { syncExtraInfo.executionHistories.size < 3 },
                startTime = pasteTask.modifyTime,
                fails = fails.values,
                extraInfo = syncExtraInfo,
            )
        }
    }
}
