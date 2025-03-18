package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
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
import com.crosspaste.sync.SyncHandler
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
        return pasteDao.getNoDeletePasteData(pasteTask.pasteDataId!!)?.let {
                pasteData ->
            val syncResults = executeSyncTasks(pasteData, syncExtraInfo)
            processResults(syncResults, syncExtraInfo, pasteTask.modifyTime)
        } ?: run {
            createEmptyResult(syncExtraInfo)
        }
    }

    private fun createEmptyResult(syncExtraInfo: SyncExtraInfo): PasteTaskResult {
        syncExtraInfo.syncFails.clear()
        return SuccessPasteTaskResult(jsonUtils.JSON.encodeToString(syncExtraInfo))
    }

    private suspend fun executeSyncTasks(
        pasteData: PasteData,
        syncExtraInfo: SyncExtraInfo,
    ): Map<String, ClientApiResult> {
        val deferredResults: MutableList<Deferred<Pair<String, ClientApiResult>>> = mutableListOf()

        for (handler in getEligibleSyncHandlers(syncExtraInfo)) {
            val deferred = createSyncTask(handler.key, handler.value, pasteData)
            deferredResults.add(deferred)
        }

        return deferredResults.associate { it.await() }
    }

    private fun getEligibleSyncHandlers(syncExtraInfo: SyncExtraInfo): Map<String, SyncHandler> {
        return syncManager.getSyncHandlers().filter { (key, handler) ->
            handler.syncRuntimeInfo.allowSend &&
                handler.versionRelation == VersionRelation.EQUAL_TO &&
                (syncExtraInfo.syncFails.isEmpty() || syncExtraInfo.syncFails.contains(key))
        }
    }

    private fun createSyncTask(
        handlerKey: String,
        handler: SyncHandler,
        pasteData: PasteData,
    ): Deferred<Pair<String, ClientApiResult>> {
        return ioScope.async {
            try {
                handler.getConnectHostAddress()?.let {
                    val hostAddress = it
                    val result = syncPasteToTarget(handlerKey, handler, pasteData, hostAddress)

                    if (result is SuccessResult) {
                        appControl.completeSendOperation()
                    }

                    return@async Pair(handlerKey, result)
                } ?: run {
                    return@async createNoHostAddressResult(handlerKey)
                }
            } catch (_: Exception) {
                createExceptionResult(handlerKey)
            }
        }
    }

    private fun createNoHostAddressResult(handlerKey: String): Pair<String, ClientApiResult> {
        return Pair(
            handlerKey,
            createFailureResult(
                StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                "Failed to get connect host address by $handlerKey",
            ),
        )
    }

    private fun createExceptionResult(handlerKey: String): Pair<String, ClientApiResult> {
        return Pair(
            handlerKey,
            createFailureResult(
                StandardErrorCode.SYNC_PASTE_ERROR,
                "Failed to sync paste to $handlerKey",
            ),
        )
    }

    private suspend fun syncPasteToTarget(
        handlerKey: String,
        handler: SyncHandler,
        pasteData: PasteData,
        hostAddress: String,
    ): ClientApiResult {
        val port = handler.syncRuntimeInfo.port
        val targetAppInstanceId = handler.syncRuntimeInfo.appInstanceId

        return if (appControl.isSendEnabled()) {
            pasteClientApi.sendPaste(
                pasteData,
                targetAppInstanceId,
            ) {
                buildUrl(hostAddress, port)
            }
        } else {
            createFailureResult(
                StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_APP,
                "Failed to send paste to $handlerKey",
            )
        }
    }

    private fun processResults(
        results: Map<String, ClientApiResult>,
        syncExtraInfo: SyncExtraInfo,
        startTime: Long,
    ): PasteTaskResult {
        val fails: Map<String, FailureResult> =
            results
                .filter { it.value is FailureResult }
                .mapValues { it.value as FailureResult }

        syncExtraInfo.syncFails.clear()

        return if (fails.isEmpty()) {
            SuccessPasteTaskResult(jsonUtils.JSON.encodeToString(syncExtraInfo))
        } else {
            val noNeedRetry =
                fails.values.any {
                    it.exception.match(StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP) ||
                        it.exception.match(StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP)
                }

            syncExtraInfo.syncFails.addAll(fails.keys)
            TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { !noNeedRetry && syncExtraInfo.executionHistories.size < 3 },
                startTime = startTime,
                fails = fails.values,
                extraInfo = syncExtraInfo,
            )
        }
    }
}
