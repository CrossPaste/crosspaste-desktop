package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
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
import com.crosspaste.net.filter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.collections.filter

class SyncPasteTaskExecutor(
    private val appControl: AppControl,
    private val appInfo: AppInfo,
    private val configManager: CommonConfigManager,
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

        if (pasteTask.pasteDataId == null) {
            return createEmptyResult(syncExtraInfo)
        }

        return pasteDao.getNoDeletePasteData(pasteTask.pasteDataId)?.let { pasteData ->
            // Check if sync is enabled for this paste type
            if (!isSyncEnabledForPasteType(pasteData)) {
                logger.debug { "Sync disabled for paste type: ${pasteData.getType().name}" }
                return@let createEmptyResult(syncExtraInfo)
            }

            val syncResults = executeSyncTasks(pasteData, syncExtraInfo)
            processResults(syncResults, syncExtraInfo, pasteTask.modifyTime)
        } ?: run {
            createEmptyResult(syncExtraInfo)
        }
    }

    private fun isSyncEnabledForPasteType(pasteData: PasteData): Boolean {
        val config = configManager.config.value
        val pasteType = pasteData.getType()
        return isSyncEnabledForType(config, pasteType)
    }

    private fun isSyncEnabledForType(
        config: AppConfig,
        pasteType: PasteType,
    ): Boolean =
        when (pasteType) {
            PasteType.TEXT_TYPE -> config.enableSyncText
            PasteType.URL_TYPE -> config.enableSyncUrl
            PasteType.HTML_TYPE -> config.enableSyncHtml
            PasteType.RTF_TYPE -> config.enableSyncRtf
            PasteType.IMAGE_TYPE -> config.enableSyncImage
            PasteType.FILE_TYPE -> config.enableSyncFile
            PasteType.COLOR_TYPE -> config.enableSyncColor
            else -> true
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

    private suspend fun getEligibleSyncHandlers(syncExtraInfo: SyncExtraInfo): Map<String, SyncHandler> =
        if (syncExtraInfo.appInstanceId == appInfo.appInstanceId) {
            syncManager.getSyncHandlers().filter { (key, handler) ->
                handler.currentSyncRuntimeInfo.allowSend &&
                    handler.currentVersionRelation == VersionRelation.EQUAL_TO &&
                    (syncExtraInfo.syncFails.isEmpty() || syncExtraInfo.syncFails.contains(key))
            }
        } else {
            syncManager.getSyncHandler(syncExtraInfo.appInstanceId)?.let { handler ->
                handler.getConnectHostInfo()?.let { connectHostInfo ->
                    syncManager.getSyncHandlers().filter { (key, handler) ->
                        if (key != syncExtraInfo.appInstanceId) {
                            val address = handler.getConnectHostAddress()
                            if (address != null && !connectHostInfo.filter(address)) {
                                handler.currentSyncRuntimeInfo.allowSend &&
                                    handler.currentVersionRelation == VersionRelation.EQUAL_TO &&
                                    (syncExtraInfo.syncFails.isEmpty() || syncExtraInfo.syncFails.contains(key))
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                }
            } ?: mapOf()
        }

    private fun createSyncTask(
        handlerKey: String,
        handler: SyncHandler,
        pasteData: PasteData,
    ): Deferred<Pair<String, ClientApiResult>> =
        ioScope.async {
            runCatching {
                handler.getConnectHostAddress()?.let {
                    val result = syncPasteToTarget(handlerKey, handler, pasteData, it)

                    if (result is SuccessResult) {
                        appControl.completeSendOperation()
                    }

                    Pair(handlerKey, result)
                } ?: run {
                    createNoHostAddressResult(handlerKey)
                }
            }.getOrElse {
                createExceptionResult(handlerKey)
            }
        }

    private fun createNoHostAddressResult(handlerKey: String): Pair<String, ClientApiResult> =
        Pair(
            handlerKey,
            createFailureResult(
                StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                "Failed to get connect host address by $handlerKey",
            ),
        )

    private fun createExceptionResult(handlerKey: String): Pair<String, ClientApiResult> =
        Pair(
            handlerKey,
            createFailureResult(
                StandardErrorCode.SYNC_PASTE_ERROR,
                "Failed to sync paste to $handlerKey",
            ),
        )

    private suspend fun syncPasteToTarget(
        handlerKey: String,
        handler: SyncHandler,
        pasteData: PasteData,
        hostAddress: String,
    ): ClientApiResult {
        val syncRuntimeInfo = handler.currentSyncRuntimeInfo
        val port = syncRuntimeInfo.port
        val targetAppInstanceId = syncRuntimeInfo.appInstanceId

        return if (appControl.isSendEnabled()) {
            val hostAndPort = HostAndPort(hostAddress, port)
            pasteClientApi.sendPaste(
                pasteData,
                targetAppInstanceId,
            ) {
                buildUrl(hostAndPort)
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
            // If any of the failures is due to non-retriable errors, do not retry
            val noNeedRetry =
                fails.values.any {
                    it.exception.match(StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP) ||
                        it.exception.match(StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP) ||
                        it.exception.match(StandardErrorCode.DECRYPT_FAIL)
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
