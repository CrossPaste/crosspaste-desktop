package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskType
import com.crosspaste.dto.notice.OversizePasteNotice
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.net.filter
import com.crosspaste.net.ws.WS_MAX_FRAME_SIZE
import com.crosspaste.net.ws.WS_MAX_PAYLOAD_SIZE
import com.crosspaste.net.ws.WsEnvelope
import com.crosspaste.net.ws.WsMessageType
import com.crosspaste.net.ws.WsPayloadSendResult
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.collections.filter

class SyncPasteTaskExecutor(
    private val appControl: AppControl,
    private val appInfo: AppInfo,
    private val configManager: CommonConfigManager,
    private val pasteDao: PasteDao,
    private val pasteClientApi: PasteClientApi,
    private val secureStore: SecureStore,
    private val syncManager: SyncManager,
    private val wsSessionManager: WsSessionManager,
    private val wsSingleFramePayloadLimit: Long = WS_MAX_FRAME_SIZE,
    private val wsChunkedPayloadLimit: Long = WS_MAX_PAYLOAD_SIZE,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    private val ioScope = namedScope(ioDispatcher, "SyncPasteTaskExecutor")

    override val taskType: Int = TaskType.SYNC_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val syncExtraInfo: SyncExtraInfo = TaskUtils.getExtraInfo(pasteTask, SyncExtraInfo::class)

        val pasteDataId =
            pasteTask.pasteDataId
                ?: return createEmptyResult(syncExtraInfo)

        return pasteDao.getNoDeletePasteData(pasteDataId)?.let { pasteData ->
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

    // Only CONNECTED handlers are eligible: a stale row left behind by a peer whose
    // appInstanceId changed keeps the last known address, so pushing to DISCONNECTED
    // handlers delivers duplicates to whoever now owns that address (#4894). A peer
    // that reconnects catches up via the pull cursor.
    private suspend fun getEligibleSyncHandlers(syncExtraInfo: SyncExtraInfo): Map<String, SyncHandler> =
        if (syncExtraInfo.appInstanceId == appInfo.appInstanceId) {
            syncManager.getSyncHandlers().filter { (key, handler) ->
                handler.currentSyncRuntimeInfo.allowSend &&
                    handler.currentSyncRuntimeInfo.connectState == SyncState.CONNECTED &&
                    handler.currentVersionRelation == VersionRelation.EQUAL_TO &&
                    (
                        syncExtraInfo.targetAppInstanceIds?.contains(key) != false
                    ) &&
                    (syncExtraInfo.syncFails.isEmpty() || syncExtraInfo.syncFails.contains(key))
            }
        } else {
            syncManager.getSyncHandler(syncExtraInfo.appInstanceId)?.let { handler ->
                handler.getConnectHostInfo()?.let { connectHostInfo ->
                    syncManager.getSyncHandlers().filter { (key, handler) ->
                        if (key != syncExtraInfo.appInstanceId) {
                            val isEligible =
                                handler.currentSyncRuntimeInfo.allowSend &&
                                    handler.currentSyncRuntimeInfo.connectState == SyncState.CONNECTED &&
                                    handler.currentVersionRelation == VersionRelation.EQUAL_TO &&
                                    (syncExtraInfo.syncFails.isEmpty() || syncExtraInfo.syncFails.contains(key))
                            if (!isEligible) {
                                false
                            } else if (handler.currentSyncRuntimeInfo.platform.isExtension()) {
                                // Extension devices use WebSocket — no host address to filter
                                true
                            } else {
                                val address = handler.getConnectHostAddress()
                                address != null && !connectHostInfo.filter(address)
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
                val result = syncPasteToTarget(handlerKey, handler, pasteData)

                if (result is SuccessResult) {
                    appControl.completeSendOperation()
                }

                Pair(handlerKey, result)
            }.getOrElse {
                createExceptionResult(handlerKey)
            }
        }

    private fun createExceptionResult(handlerKey: String): Pair<String, ClientApiResult> =
        Pair(
            handlerKey,
            createFailureResult(
                StandardErrorCode.SYNC_PASTE_ERROR,
                "Failed to sync paste to $handlerKey",
            ),
        )

    companion object {
        /** Chrome extension per-file size limit (1MB). */
        const val EXTENSION_MAX_FILE_SIZE: Long = 1L * 1024 * 1024

        /** Chrome extension total files size limit (32MB). */
        const val EXTENSION_MAX_TOTAL_FILE_SIZE: Long = 32L * 1024 * 1024
    }

    private suspend fun syncPasteToTarget(
        handlerKey: String,
        handler: SyncHandler,
        pasteData: PasteData,
    ): ClientApiResult {
        val syncRuntimeInfo = handler.currentSyncRuntimeInfo
        val port = syncRuntimeInfo.port
        val targetAppInstanceId = syncRuntimeInfo.appInstanceId

        if (!appControl.isSendEnabled()) {
            return createFailureResult(
                StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_APP,
                "Failed to send paste to $handlerKey",
            )
        }

        // Extension devices have file size limits — skip file-type pastes that exceed them
        if (syncRuntimeInfo.platform.isExtension() && pasteData.isFileType()) {
            val pasteFiles = pasteData.getPasteItem(PasteFiles::class)
            val oversizeNotice =
                if (pasteFiles == null) null else detectExtensionOversize(pasteFiles)
            if (pasteFiles == null || oversizeNotice != null) {
                logger.info {
                    "Skipping file-type paste sync to extension $handlerKey: " +
                        if (pasteFiles == null) {
                            "unable to resolve PasteFiles for size check"
                        } else {
                            "file size exceeds extension limit (reason=${oversizeNotice?.reason})"
                        }
                }
                if (oversizeNotice != null) {
                    notifyExtensionOversize(targetAppInstanceId, oversizeNotice)
                }
                return SuccessResult()
            }
        }

        // 1. Extension targets always use WebSocket
        if (syncRuntimeInfo.platform.isExtension()) {
            return trySendViaWebSocket(targetAppInstanceId, pasteData, isExtensionTarget = true)
                ?: createFailureResult(
                    StandardErrorCode.SYNC_PASTE_ERROR,
                    "WebSocket send failed for extension $handlerKey",
                )
        }

        // 2. Regular devices prefer HTTP (supports concurrent chunk transfer for large files)
        val hostAddress = handler.getConnectHostAddress()
        if (hostAddress != null) {
            val hostAndPort = HostAndPort(hostAddress, port)
            return pasteClientApi.sendPaste(
                pasteData,
                targetAppInstanceId,
            ) {
                buildUrl(hostAndPort)
            }
        }

        // 3. No host address — fall back to WebSocket
        return trySendViaWebSocket(targetAppInstanceId, pasteData, isExtensionTarget = false)
            ?: createFailureResult(
                StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                "Failed to get connect host address by $handlerKey and WebSocket unavailable",
            )
    }

    private suspend fun trySendViaWebSocket(
        targetAppInstanceId: String,
        pasteData: PasteData,
        isExtensionTarget: Boolean,
    ): ClientApiResult? =
        runCatching {
            val jsonBytes = getJsonUtils().JSON.encodeToString(pasteData).encodeToByteArray()
            val encrypt = configManager.getCurrentConfig().enableEncryptSync
            val payload =
                if (encrypt) {
                    secureStore.getMessageProcessor(targetAppInstanceId).encrypt(jsonBytes)
                } else {
                    jsonBytes
                }
            val envelope =
                WsEnvelope(
                    type = WsMessageType.PASTE_PUSH,
                    payload = payload,
                    encrypted = encrypt,
                )
            // Native pairing v2 deliberately keeps the 1 MiB WebSocket payload
            // contract; file bytes use HTTPS. Pairing v3 opts into the larger
            // logical-message cap through chunking. Extension clients remain
            // single-frame but use the browser-compatible message cap. The
            // manager selects the limit and sends through one session snapshot,
            // so a reconnect cannot change capability between those operations.
            val singleFramePayloadLimit =
                if (isExtensionTarget) wsChunkedPayloadLimit else wsSingleFramePayloadLimit
            when (
                val sendResult =
                    wsSessionManager.sendWithPayloadLimits(
                        appInstanceId = targetAppInstanceId,
                        envelope = envelope,
                        singleFramePayloadLimit = singleFramePayloadLimit,
                        chunkedPayloadLimit = wsChunkedPayloadLimit,
                    )
            ) {
                WsPayloadSendResult.Sent -> SuccessResult()
                WsPayloadSendResult.Failed -> null // WebSocket send failed, fall back to HTTP
                is WsPayloadSendResult.PayloadTooLarge -> {
                    logger.warn {
                        "WS paste push to $targetAppInstanceId rejected: " +
                            "payload ${sendResult.payloadSize} bytes exceeds limit ${sendResult.payloadLimit}"
                    }
                    createFailureResult(
                        StandardErrorCode.SYNC_PASTE_ERROR,
                        "Paste payload ${sendResult.payloadSize} bytes exceeds " +
                            "WebSocket limit for $targetAppInstanceId",
                    )
                }
            }
        }.onFailure { e ->
            logger.warn(e) { "WebSocket paste send failed for $targetAppInstanceId, falling back to HTTP" }
        }.getOrNull()

    /**
     * Returns a notice describing why [pasteFiles] exceeds the Chrome extension
     * per-file / total-size limits, or null if it's within bounds.
     */
    private fun detectExtensionOversize(pasteFiles: PasteFiles): OversizePasteNotice? {
        val oversizeFile =
            pasteFiles.fileInfoTreeMap.entries.firstOrNull { (_, info) ->
                info.size > EXTENSION_MAX_FILE_SIZE
            }
        if (oversizeFile != null) {
            return OversizePasteNotice(
                reason = OversizePasteNotice.Reason.FILE_TOO_LARGE,
                fileName = oversizeFile.key,
                actualSize = oversizeFile.value.size,
                sizeLimitBytes = EXTENSION_MAX_FILE_SIZE,
            )
        }
        if (pasteFiles.size > EXTENSION_MAX_TOTAL_FILE_SIZE) {
            return OversizePasteNotice(
                reason = OversizePasteNotice.Reason.TOTAL_TOO_LARGE,
                fileName = null,
                actualSize = pasteFiles.size,
                sizeLimitBytes = EXTENSION_MAX_TOTAL_FILE_SIZE,
            )
        }
        return null
    }

    private suspend fun notifyExtensionOversize(
        targetAppInstanceId: String,
        notice: OversizePasteNotice,
    ) {
        runCatching {
            val payload = getJsonUtils().JSON.encodeToString(notice).encodeToByteArray()
            val envelope =
                WsEnvelope(
                    type = WsMessageType.PASTE_REJECTED_OVERSIZE,
                    payload = payload,
                )
            wsSessionManager.send(targetAppInstanceId, envelope)
        }.onFailure { e ->
            logger.warn(e) { "Failed to send PASTE_REJECTED_OVERSIZE to $targetAppInstanceId" }
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
            // Retry decisions are per target: a non-retriable failure (e.g. a stale
            // row's NOT_MATCH_APP_INSTANCE_ID) only drops its own target from
            // syncFails, so transient failures on other targets still get retried.
            val retriableFails = fails.filterValues { !isNonRetriable(it) }

            syncExtraInfo.syncFails.addAll(retriableFails.keys)
            TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { retriableFails.isNotEmpty() && syncExtraInfo.executionHistories.size < 3 },
                startTime = startTime,
                fails = fails.values,
                extraInfo = syncExtraInfo,
            )
        }
    }

    private fun isNonRetriable(failure: FailureResult): Boolean =
        failure.exception.match(StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP) ||
            failure.exception.match(StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_APP) ||
            failure.exception.match(StandardErrorCode.DECRYPT_FAIL) ||
            failure.exception.match(StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID)
}
