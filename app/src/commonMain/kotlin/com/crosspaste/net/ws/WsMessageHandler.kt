package com.crosspaste.net.ws

import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.dto.pull.WsPullFileRequest
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WsMessageHandler(
    private val lazyAppControl: Lazy<AppControl>,
    private val lazyCacheManager: Lazy<CacheManager>,
    private val lazyPasteDao: Lazy<PasteDao>,
    private val lazyPasteboardService: Lazy<PasteboardService>,
    private val lazySyncRoutingApi: Lazy<SyncRoutingApi>,
    private val secureStore: SecureStore,
    private val userDataPathProvider: UserDataPathProvider,
    private val wsPendingRequests: WsPendingRequests,
    private val wsSessionManager: WsSessionManager,
    private val scope: CoroutineScope = namedScope(ioDispatcher, "WsMessageHandler"),
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
                handleFilePullResponse(envelope)
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
                return
            }

        if (!syncHandler.currentSyncRuntimeInfo.allowReceive) {
            logger.debug { "WS paste_push from $appInstanceId: user not allow receive" }
            return
        }

        if (!appControl.isReceiveEnabled()) {
            logger.debug { "WS paste_push from $appInstanceId: app not allow receive" }
            return
        }

        runCatching {
            val payloadBytes =
                if (envelope.encrypted) {
                    secureStore.getMessageProcessor(appInstanceId).decrypt(envelope.payload)
                } else {
                    envelope.payload
                }
            val pasteData =
                json
                    .decodeFromString<PasteData>(payloadBytes.decodeToString())
                    .copy(remote = true)

            scope.launch {
                pasteboardService.tryWriteRemotePasteboard(pasteData)
            }
            appControl.completeReceiveOperation()
            logger.debug { "WS paste_push from $appInstanceId processed successfully" }
        }.onFailure { e ->
            logger.error(e) { "WS paste_push from $appInstanceId failed" }
        }
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

        // Find the file across all PasteFiles items in this paste
        val allFileItems = pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()
        var targetPath: okio.Path? = null

        for (pasteFiles in allFileItems) {
            val filePaths = pasteFiles.getFilePaths(userDataPathProvider)
            for (filePath in filePaths) {
                if (filePath.name == request.fileName) {
                    targetPath = filePath
                    break
                }
            }
            if (targetPath != null) break
        }

        if (targetPath == null) {
            logger.error { "FILE_PULL_REQUEST whole-file: file '${request.fileName}' not found in paste ${request.id}" }
            sendErrorResponse(appInstanceId, requestId, "File not found: ${request.fileName}")
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
        if (requestId != null && wsPendingRequests.complete(requestId, envelope)) {
            logger.debug { "Error response from $appInstanceId routed to pending request $requestId" }
        } else {
            val msg = if (envelope.payload.isNotEmpty()) envelope.payload.decodeToString() else "(no detail)"
            logger.warn { "WS error from $appInstanceId (requestId=$requestId): $msg" }
        }
    }

    private fun handleFilePullResponse(envelope: WsEnvelope) {
        val requestId = envelope.requestId
        if (requestId == null) {
            logger.warn { "FILE_PULL_RESPONSE missing requestId" }
            return
        }
        if (!wsPendingRequests.complete(requestId, envelope)) {
            logger.warn { "FILE_PULL_RESPONSE: no pending request for requestId=$requestId" }
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
