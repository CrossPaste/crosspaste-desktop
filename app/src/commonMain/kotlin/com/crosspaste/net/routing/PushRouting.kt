package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushHeaders
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileTransferResourceLimits
import com.crosspaste.sync.PushCompletionResult
import com.crosspaste.sync.PushSession
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.requireSyncHandler
import com.crosspaste.utils.requireTargetAppInstance
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import okio.Path
import okio.Path.Companion.toPath

private val logger: KLogger = KotlinLogging.logger("PushRouting")
private val fileUtils: FileUtils = getFileUtils()

/**
 * Permission gating policy: /sync/paste (push mode) is the only place where
 * we check `appControl.isReceiveEnabled` + `syncHandler.allowReceive`. Once
 * a PushSession exists, subsequent chunk uploads and /complete trust the
 * session token — toggling permissions mid-session does NOT tear down
 * in-flight work. Sender already knows the answer from the prepare response.
 * SyncHandler lookup is kept as a peer-existence sanity check (cheap),
 * its allowReceive flag is intentionally not consulted here.
 */
fun Routing.pushRouting(
    appInfo: AppInfo,
    pushSessionManager: PushSessionManager,
    syncRoutingApi: SyncRoutingApi,
    userDataPathProvider: UserDataPathProvider,
) {
    post("/sync/file/push") {
        handleFilePushChunk(call, appInfo, pushSessionManager, syncRoutingApi)
    }
    post("/sync/paste/push/complete") {
        handleCompletePush(call, appInfo, pushSessionManager)
    }
    post("/sync/icon/push/{source}") {
        handleIconPush(call, appInfo, syncRoutingApi, userDataPathProvider)
    }
}

private data class PushChunkHeaders(
    val pasteId: Long,
    val chunkIndex: Int,
    val token: String,
)

private suspend fun parsePushChunkHeaders(call: ApplicationCall): PushChunkHeaders? {
    val pasteId = call.request.headers[PushHeaders.PASTE_ID]?.toLongOrNull()
    val chunkIndex = call.request.headers[PushHeaders.CHUNK_INDEX]?.toIntOrNull()
    val token = call.request.headers[PushHeaders.SESSION_TOKEN]
    if (pasteId == null || chunkIndex == null || token == null) {
        failResponse(call, StandardErrorCode.INVALID_PARAMETER.toErrorCode())
        return null
    }
    return PushChunkHeaders(pasteId, chunkIndex, token)
}

private suspend fun handleFilePushChunk(
    call: ApplicationCall,
    appInfo: AppInfo,
    pushSessionManager: PushSessionManager,
    syncRoutingApi: SyncRoutingApi,
) {
    val fromAppInstanceId = getAppInstanceId(call) ?: return
    if (!requireTargetAppInstance(call, appInfo)) return
    requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return

    val headers = parsePushChunkHeaders(call) ?: return

    val session = pushSessionManager.get(headers.pasteId, headers.token, fromAppInstanceId)
    if (session == null) {
        logger.debug { "push chunk: session not found pasteId=${headers.pasteId} from=$fromAppInstanceId" }
        failResponse(call, StandardErrorCode.NOT_FOUND_PUSH_SESSION.toErrorCode())
        return
    }

    if (headers.chunkIndex !in 0 until session.chunkCount) {
        failResponse(
            call,
            StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
            "chunk index ${headers.chunkIndex} out of range (chunkCount=${session.chunkCount})",
        )
        return
    }

    // Fast-path idempotency: drain the body and ack without writing.
    if (session.isReceived(headers.chunkIndex)) {
        call.receiveChannel().discard()
        finishReceivedChunk(call, pushSessionManager, session)
        return
    }

    val filesChunk =
        session.filesIndex.getChunk(headers.chunkIndex) ?: run {
            failResponse(
                call,
                StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                "chunk index ${headers.chunkIndex} out of range in filesIndex",
            )
            return
        }

    val writeFailed =
        runCatching {
            fileUtils.writeFilesChunk(filesChunk, call.receiveChannel())
        }.onFailure { e ->
            logger.error(e) { "failed to write files chunk" }
        }.isFailure
    if (writeFailed) {
        logger.error { "push chunk: write failed pasteId=${headers.pasteId} chunkIndex=${headers.chunkIndex}" }
        failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
        return
    }

    finishChunkPush(call, pushSessionManager, session, headers)
}

private suspend fun finishChunkPush(
    call: ApplicationCall,
    pushSessionManager: PushSessionManager,
    session: PushSession,
    headers: PushChunkHeaders,
) {
    val markResult = session.markReceived(headers.chunkIndex)
    when (markResult) {
        PushSession.MarkResult.Accepted,
        PushSession.MarkResult.AlreadyReceived,
        -> finishReceivedChunk(call, pushSessionManager, session)

        PushSession.MarkResult.OutOfRange ->
            failResponse(
                call,
                StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                "chunk index ${headers.chunkIndex} out of range",
            )
    }
}

private suspend fun finishReceivedChunk(
    call: ApplicationCall,
    pushSessionManager: PushSessionManager,
    session: PushSession,
) {
    if (!session.isComplete) {
        successResponse(call)
        return
    }

    // The last chunk is not acknowledged until LOADING -> LOADED and its
    // follow-up tasks have been persisted. A retry of the same chunk waits on
    // the same per-session finalize lock.
    when (val result = pushSessionManager.finalizeIfComplete(session)) {
        PushCompletionResult.Complete -> successResponse(call)
        is PushCompletionResult.Incomplete -> successResponse(call)
        PushCompletionResult.NotFound ->
            failResponse(call, StandardErrorCode.NOT_FOUND_PUSH_SESSION.toErrorCode())
        is PushCompletionResult.Failed -> {
            logger.warn(result.cause) { "push chunk: finalization failed pasteId=${session.pasteId}" }
            failResponse(call, StandardErrorCode.PUSH_COMPLETE_FAIL.toErrorCode())
        }
    }
}

private suspend fun handleCompletePush(
    call: ApplicationCall,
    appInfo: AppInfo,
    pushSessionManager: PushSessionManager,
) {
    val fromAppInstanceId = getAppInstanceId(call) ?: return
    if (!requireTargetAppInstance(call, appInfo)) return

    val pasteId = call.request.headers[PushHeaders.PASTE_ID]?.toLongOrNull()
    val token = call.request.headers[PushHeaders.SESSION_TOKEN]
    if (pasteId == null || token == null) {
        failResponse(call, StandardErrorCode.INVALID_PARAMETER.toErrorCode())
        return
    }

    when (val result = pushSessionManager.complete(pasteId, token, fromAppInstanceId)) {
        PushCompletionResult.Complete -> {
            logger.info { "push complete: pasteId=$pasteId durably finalized" }
            successResponse(call, PushCompleteResponse(emptyList()))
        }
        is PushCompletionResult.Incomplete -> {
            logger.debug { "push complete: pasteId=$pasteId missing ${result.missingChunks.size} chunks" }
            successResponse(call, PushCompleteResponse(result.missingChunks))
        }
        PushCompletionResult.NotFound -> {
            logger.debug { "push complete: session not found pasteId=$pasteId from=$fromAppInstanceId" }
            failResponse(call, StandardErrorCode.NOT_FOUND_PUSH_SESSION.toErrorCode())
        }
        is PushCompletionResult.Failed -> {
            logger.warn(result.cause) { "push complete: finalization failed pasteId=$pasteId" }
            failResponse(call, StandardErrorCode.PUSH_COMPLETE_FAIL.toErrorCode())
        }
    }
}

private suspend fun handleIconPush(
    call: ApplicationCall,
    appInfo: AppInfo,
    syncRoutingApi: SyncRoutingApi,
    userDataPathProvider: UserDataPathProvider,
) {
    val fromAppInstanceId = getAppInstanceId(call) ?: return
    if (!requireTargetAppInstance(call, appInfo)) return
    // Same meta-only permission gating as /sync/file/push: icons are a
    // continuation of an already-authorized push, no re-check of allowReceive.
    requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return

    val source =
        call.parameters["source"] ?: run {
            failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
            return
        }

    val iconPath =
        runCatching {
            userDataPathProvider.resolveIconPath(fromAppInstanceId, source)
        }.getOrElse {
            logger.warn { "push icon: invalid source from=$fromAppInstanceId source=$source" }
            failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
            return
        }

    val writeFailed = !writeIconAtomically(iconPath, call.receiveChannel())
    if (writeFailed) {
        logger.error { "push icon: write failed source=$source from=$fromAppInstanceId" }
        failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
        return
    }

    logger.info { "push icon: stored source=$source from=$fromAppInstanceId" }
    successResponse(call)
}

/**
 * Writes an icon body to a uniquely named sibling temp file and atomically
 * moves it into place. The unique name keeps concurrent uploads for the same
 * source from sharing a temp file, and the move ensures an oversized or
 * aborted upload never leaves a truncated icon at [iconPath] (a truncated
 * icon would suppress the pull-icon repair path because the file exists).
 */
internal suspend fun writeIconAtomically(
    iconPath: Path,
    byteReadChannel: ByteReadChannel,
): Boolean {
    val tempIconPath = "$iconPath.${fileUtils.createRandomFileName("part")}".toPath()
    val moved =
        runCatching {
            fileUtils.writeFile(
                path = tempIconPath,
                byteReadChannel = byteReadChannel,
                maxBytes = FileTransferResourceLimits.MAX_ICON_SIZE,
            )
            fileUtils.moveFile(tempIconPath, iconPath).getOrThrow()
        }.isSuccess
    if (!moved) {
        fileUtils.deleteFile(tempIconPath)
    }
    return moved
}
