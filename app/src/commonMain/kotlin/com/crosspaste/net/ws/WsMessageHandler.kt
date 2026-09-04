package com.crosspaste.net.ws

import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.dto.pull.WsPullFileRequest
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.routing.bindAuthenticatedRemoteIdentity
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

class WsMessageHandler(
    private val lazyAppControl: Lazy<AppControl>,
    private val lazyCacheManager: Lazy<CacheManager>,
    private val lazyPasteDao: Lazy<PasteDao>,
    private val lazyPasteboardService: Lazy<PasteboardService>,
    private val lazySyncRoutingApi: Lazy<SyncRoutingApi>,
    private val secureStore: SecureStore,
    private val userDataPathProvider: UserDataPathProvider,
    private val wsSessionManager: WsSessionManager,
) {
    private val appControl: AppControl get() = lazyAppControl.value
    private val cacheManager: CacheManager get() = lazyCacheManager.value
    private val pasteDao: PasteDao get() = lazyPasteDao.value
    private val pasteboardService: PasteboardService get() = lazyPasteboardService.value
    private val syncRoutingApi: SyncRoutingApi get() = lazySyncRoutingApi.value
    private val logger = KotlinLogging.logger {}
    private val fileUtils = getFileUtils()
    private val json = getJsonUtils().JSON

    suspend fun handleMessage(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        logger.debug { "WS message from $appInstanceId: type=${envelope.type}" }

        when (envelope.type) {
            WsMessageType.HEARTBEAT -> {
                wsSessionManager.send(
                    appInstanceId,
                    WsEnvelope(type = WsMessageType.HEARTBEAT_ACK),
                )
            }

            WsMessageType.HEARTBEAT_ACK -> {
                // Peer acknowledged our heartbeat — connection is alive
                logger.debug { "Heartbeat ACK from $appInstanceId" }
            }

            WsMessageType.PASTE_PUSH -> {
                handlePastePush(appInstanceId, envelope)
            }

            WsMessageType.PASTE_PUSH_ACK -> {
                handleResponse(appInstanceId, envelope)
            }

            WsMessageType.NOTIFY_EXIT -> {
                logger.info { "WS notify exit from $appInstanceId" }
                syncRoutingApi.markExit(appInstanceId)
            }

            WsMessageType.NOTIFY_REMOVE -> {
                logger.info { "WS notify remove from $appInstanceId" }
                syncRoutingApi.removeSyncHandler(appInstanceId)
            }

            WsMessageType.FILE_PULL_REQUEST -> {
                handleFilePullRequest(appInstanceId, envelope)
            }

            WsMessageType.FILE_PULL_RESPONSE -> {
                handleResponse(appInstanceId, envelope)
            }

            WsMessageType.ERROR -> {
                handleErrorResponse(appInstanceId, envelope)
            }

            else -> {
                logger.warn { "Unknown WS message type: ${envelope.type} from $appInstanceId" }
            }
        }
    }

    private suspend fun handlePastePush(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        val syncHandler =
            syncRoutingApi.getSyncHandler(appInstanceId) ?: run {
                logger.error { "WS paste_push: no sync handler for $appInstanceId" }
                sendPastePushError(appInstanceId, envelope.requestId, "No sync handler")
                return
            }

        if (!syncHandler.currentSyncRuntimeInfo.allowReceive) {
            logger.debug { "WS paste_push from $appInstanceId: user not allow receive" }
            sendPastePushError(appInstanceId, envelope.requestId, "Receiving disabled for device")
            return
        }

        if (!appControl.isReceiveEnabled()) {
            logger.debug { "WS paste_push from $appInstanceId: app not allow receive" }
            sendPastePushError(appInstanceId, envelope.requestId, "Receiving disabled by app")
            return
        }

        val ingestResult =
            try {
                val payloadBytes =
                    if (envelope.encrypted) {
                        secureStore.getMessageProcessor(appInstanceId).decrypt(envelope.payload)
                    } else {
                        envelope.payload
                    }
                val pasteData =
                    json
                        .decodeFromString<PasteData>(payloadBytes.decodeToString())
                        .also { pasteData ->
                            if (pasteData.appInstanceId != appInstanceId) {
                                logger.warn {
                                    "Ignoring mismatched PasteData identity from authenticated WS peer $appInstanceId"
                                }
                            }
                        }.bindAuthenticatedRemoteIdentity(appInstanceId)

                val accepted = pasteboardService.tryWriteRemotePasteboard(pasteData).getOrThrow()
                if (accepted == null) {
                    sendPastePushError(appInstanceId, envelope.requestId, "Paste ingestion rejected")
                    return
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "WS paste_push from $appInstanceId failed" }
                sendPastePushError(appInstanceId, envelope.requestId, "Paste ingestion failed")
                false
            }
        if (!ingestResult) return

        logger.debug { "WS paste_push from $appInstanceId ingested successfully" }
        envelope.requestId?.let { requestId ->
            wsSessionManager.send(
                appInstanceId,
                WsEnvelope(
                    type = WsMessageType.PASTE_PUSH_ACK,
                    requestId = requestId,
                ),
            )
        }

        try {
            appControl.completeReceiveOperation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to record completed WS receive operation from $appInstanceId" }
        }
    }

    private suspend fun sendPastePushError(
        appInstanceId: String,
        requestId: String?,
        message: String,
    ) {
        requestId?.let { sendErrorResponse(appInstanceId, it, message) }
    }

    private suspend fun handleFilePullRequest(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        val requestId = envelope.requestId
        if (requestId == null) {
            logger.warn { "FILE_PULL_REQUEST from $appInstanceId missing requestId" }
            return
        }

        withErrorResponse(appInstanceId, requestId, "FILE_PULL_REQUEST") {
            val request = json.decodeFromString<WsPullFileRequest>(envelope.payload.decodeToString())
            logger.debug { "FILE_PULL_REQUEST from $appInstanceId: $request" }

            val syncHandler =
                syncRoutingApi.getSyncHandler(appInstanceId) ?: run {
                    logger.error { "FILE_PULL_REQUEST: no sync handler for $appInstanceId" }
                    sendErrorResponse(appInstanceId, requestId, "No sync handler")
                    return@withErrorResponse
                }

            if (!syncHandler.currentSyncRuntimeInfo.allowSend) {
                logger.debug { "FILE_PULL_REQUEST from $appInstanceId: not allow send" }
                sendErrorResponse(appInstanceId, requestId, "Not allowed to send")
                return@withErrorResponse
            }

            when (request) {
                is WsPullFileRequest.ChunkRequest -> serveFileChunk(appInstanceId, requestId, request)
                is WsPullFileRequest.WholeFileRequest -> serveWholeFile(appInstanceId, requestId, request)
            }
        }
    }

    private suspend fun serveFileChunk(
        appInstanceId: String,
        requestId: String,
        request: WsPullFileRequest.ChunkRequest,
    ) {
        val filesIndex =
            cacheManager.getFilesIndex(request.id) ?: run {
                logger.error { "FILE_PULL_REQUEST: filesIndex not found for id=${request.id}" }
                sendErrorResponse(appInstanceId, requestId, "FilesIndex not found")
                return
            }

        val chunk =
            filesIndex.getChunk(request.chunkIndex) ?: run {
                logger.error { "FILE_PULL_REQUEST: chunk index out of range: ${request.chunkIndex}" }
                sendErrorResponse(appInstanceId, requestId, "Chunk index out of range")
                return
            }

        // Read the chunk's file segments into a byte array
        val bytes = fileUtils.readFilesChunkToByteArray(chunk)

        val response =
            WsEnvelope(
                type = WsMessageType.FILE_PULL_RESPONSE,
                payload = bytes,
                requestId = requestId,
            )
        wsSessionManager.send(appInstanceId, response)
        logger.debug { "Served file chunk ${request.chunkIndex} (${bytes.size} bytes) to $appInstanceId" }
    }

    /**
     * Serve a single file by paste ID + fileName (whole-file mode).
     * Used when Chrome extension pulls files from Desktop.
     * Files are guaranteed ≤ 1MB by SyncPasteTaskExecutor's extension filter.
     */
    private suspend fun serveWholeFile(
        appInstanceId: String,
        requestId: String,
        request: WsPullFileRequest.WholeFileRequest,
    ) {
        val pasteData =
            pasteDao.getNoDeletePasteData(request.id) ?: run {
                logger.error { "FILE_PULL_REQUEST whole-file: paste not found for id=${request.id}" }
                sendErrorResponse(appInstanceId, requestId, "Paste not found")
                return
            }

        val candidates =
            pasteData
                .getPasteAppearItems()
                .filterIsInstance<PasteFiles>()
                .flatMap { pasteFiles ->
                    pasteFiles.relativePathList
                        .zip(pasteFiles.getFilePaths(userDataPathProvider))
                        .map { (relativePath, filePath) -> WholeFileCandidate(relativePath, filePath) }
                }
        val targetPath = selectWholeFilePath(candidates, request)

        if (targetPath == null) {
            logger.error {
                "FILE_PULL_REQUEST whole-file: file '${request.fileName}' not found or ambiguous in paste ${request.id}"
            }
            sendErrorResponse(appInstanceId, requestId, "File not found or ambiguous: ${request.fileName}")
            return
        }

        if (!fileUtils.existFile(targetPath)) {
            logger.error { "FILE_PULL_REQUEST whole-file: file does not exist on disk: $targetPath" }
            sendErrorResponse(appInstanceId, requestId, "File not found on disk")
            return
        }

        val bytes = fileUtils.fileSystem.read(targetPath) { readByteArray() }

        val response =
            WsEnvelope(
                type = WsMessageType.FILE_PULL_RESPONSE,
                payload = bytes,
                requestId = requestId,
            )
        wsSessionManager.send(appInstanceId, response)
        logger.debug { "Served whole file '${request.fileName}' (${bytes.size} bytes) to $appInstanceId" }
    }

    private fun handleErrorResponse(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        val requestId = envelope.requestId
        if (requestId != null && wsSessionManager.completePendingRequest(requestId, envelope)) {
            logger.debug { "Error response from $appInstanceId routed to pending request $requestId" }
        } else {
            val msg = if (envelope.payload.isNotEmpty()) envelope.payload.decodeToString() else "(no detail)"
            logger.warn { "WS error from $appInstanceId (requestId=$requestId): $msg" }
        }
    }

    private fun handleResponse(
        appInstanceId: String,
        envelope: WsEnvelope,
    ) {
        val requestId = envelope.requestId
        if (requestId == null) {
            logger.warn { "${envelope.type} from $appInstanceId missing requestId" }
            return
        }
        if (!wsSessionManager.completePendingRequest(requestId, envelope)) {
            logger.warn { "${envelope.type} from $appInstanceId has no pending request: $requestId" }
        }
    }

    private suspend inline fun withErrorResponse(
        appInstanceId: String,
        requestId: String,
        label: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            logger.error(e) { "$label from $appInstanceId failed" }
            try {
                sendErrorResponse(appInstanceId, requestId, "Internal error: ${e.message}")
            } catch (sendError: Exception) {
                logger.error(sendError) { "$label: failed to send error response to $appInstanceId" }
            }
        }
    }

    private suspend fun sendErrorResponse(
        appInstanceId: String,
        requestId: String,
        message: String,
    ) {
        val errorEnvelope =
            WsEnvelope(
                type = WsMessageType.ERROR,
                payload = message.encodeToByteArray(),
                requestId = requestId,
            )
        wsSessionManager.send(appInstanceId, errorEnvelope)
    }
}

internal data class WholeFileCandidate(
    val relativePath: String,
    val filePath: okio.Path,
)

internal fun selectWholeFilePath(
    candidates: List<WholeFileCandidate>,
    request: WsPullFileRequest.WholeFileRequest,
): okio.Path? {
    val matches =
        request.relativePath?.let { requestedPath ->
            candidates.filter { it.relativePath == requestedPath }
        } ?: candidates.filter { it.filePath.name == request.fileName }
    return matches.singleOrNull()?.filePath
}
