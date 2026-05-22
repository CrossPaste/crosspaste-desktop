package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushHeaders
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.sync.PushSession
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.requireSyncHandler
import com.crosspaste.utils.requireTargetAppInstance
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Routing.pushRouting(
    appInfo: AppInfo,
    pushSessionManager: PushSessionManager,
    syncRoutingApi: SyncRoutingApi,
    userDataPathProvider: UserDataPathProvider,
) {
    val logger = KotlinLogging.logger {}
    val fileUtils = getFileUtils()

    // Permission gating policy: /sync/paste (push mode) is the only place where
    // we check `appControl.isReceiveEnabled` + `syncHandler.allowReceive`. Once
    // a PushSession exists, subsequent chunk uploads and /complete trust the
    // session token — toggling permissions mid-session does NOT tear down
    // in-flight work. Sender already knows the answer from the prepare response.
    // SyncHandler lookup is kept as a peer-existence sanity check (cheap),
    // its allowReceive flag is intentionally not consulted here.
    post("/sync/file/push") {
        val fromAppInstanceId = getAppInstanceId(call) ?: return@post
        if (!requireTargetAppInstance(call, appInfo)) return@post
        requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return@post

        val pasteId = call.request.headers[PushHeaders.PASTE_ID]?.toLongOrNull()
        val chunkIndex = call.request.headers[PushHeaders.CHUNK_INDEX]?.toIntOrNull()
        val token = call.request.headers[PushHeaders.SESSION_TOKEN]
        if (pasteId == null || chunkIndex == null || token == null) {
            failResponse(call, StandardErrorCode.INVALID_PARAMETER.toErrorCode())
            return@post
        }

        val session = pushSessionManager.get(pasteId, token, fromAppInstanceId)
        if (session == null) {
            logger.debug { "push chunk: session not found pasteId=$pasteId from=$fromAppInstanceId" }
            failResponse(call, StandardErrorCode.NOT_FOUND_PUSH_SESSION.toErrorCode())
            return@post
        }

        if (chunkIndex !in 0 until session.chunkCount) {
            failResponse(
                call,
                StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                "chunk index $chunkIndex out of range (chunkCount=${session.chunkCount})",
            )
            return@post
        }

        // Fast-path idempotency: drain the body and ack without writing.
        if (session.isReceived(chunkIndex)) {
            call.receiveChannel().discard()
            successResponse(call)
            return@post
        }

        val filesChunk =
            session.filesIndex.getChunk(chunkIndex) ?: run {
                failResponse(
                    call,
                    StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                    "chunk index $chunkIndex out of range in filesIndex",
                )
                return@post
            }

        runCatching {
            fileUtils.writeFilesChunk(filesChunk, call.receiveChannel())
        }.onFailure { e ->
            logger.error(e) { "push chunk: write failed pasteId=$pasteId chunkIndex=$chunkIndex" }
            failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
            return@post
        }

        val markResult = session.markReceived(chunkIndex)
        if (markResult == PushSession.MarkResult.Accepted) {
            // Auto-finalize the moment the last chunk lands — don't wait for
            // /complete (the client may never send it, e.g. Share Extension
            // crash) or for the sweep poll (90s of latency).
            pushSessionManager.tryFinalizeIfComplete(pasteId)
        }
        when (markResult) {
            PushSession.MarkResult.Accepted,
            PushSession.MarkResult.AlreadyReceived,
            -> successResponse(call)

            PushSession.MarkResult.OutOfRange ->
                failResponse(
                    call,
                    StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                    "chunk index $chunkIndex out of range",
                )
        }
    }

    post("/sync/paste/push/complete") {
        val fromAppInstanceId = getAppInstanceId(call) ?: return@post
        if (!requireTargetAppInstance(call, appInfo)) return@post

        val pasteId = call.request.headers[PushHeaders.PASTE_ID]?.toLongOrNull()
        val token = call.request.headers[PushHeaders.SESSION_TOKEN]
        if (pasteId == null || token == null) {
            failResponse(call, StandardErrorCode.INVALID_PARAMETER.toErrorCode())
            return@post
        }

        val session = pushSessionManager.get(pasteId, token, fromAppInstanceId)
        if (session == null) {
            // Session is gone — almost always because auto-finalize on the last
            // chunk already ran. Return success so the client doesn't retry.
            // Wrong pasteId/token leaks nothing exploitable (the peer is already
            // authenticated via secureStore; pasteIds are not secret).
            logger.debug { "push complete: pasteId=$pasteId session absent (likely auto-finalized)" }
            successResponse(call, PushCompleteResponse(emptyList()))
            return@post
        }

        val missing = session.missingChunks()
        if (missing.isNotEmpty()) {
            logger.debug {
                "push complete: pasteId=$pasteId missing ${missing.size}/${session.chunkCount} chunks"
            }
            successResponse(call, PushCompleteResponse(missing))
            return@post
        }

        // Defensive: auto-finalize should have caught this already, but races
        // happen. tryFinalize is idempotent — returns false if someone got here first.
        if (pushSessionManager.tryFinalize(pasteId)) {
            logger.info { "push complete: pasteId=$pasteId finalized via /complete" }
        } else {
            logger.info { "push complete: pasteId=$pasteId already finalized" }
        }
        successResponse(call, PushCompleteResponse(emptyList()))
    }

    post("/sync/icon/push/{source}") {
        val fromAppInstanceId = getAppInstanceId(call) ?: return@post
        if (!requireTargetAppInstance(call, appInfo)) return@post
        // Same meta-only permission gating as /sync/file/push: icons are a
        // continuation of an already-authorized push, no re-check of allowReceive.
        requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return@post

        val source =
            call.parameters["source"] ?: run {
                failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
                return@post
            }

        val iconPath =
            runCatching {
                userDataPathProvider.resolveIconPath(fromAppInstanceId, source)
            }.getOrElse {
                logger.warn { "push icon: invalid source from=$fromAppInstanceId source=$source" }
                failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
                return@post
            }

        runCatching {
            fileUtils.writeFile(iconPath, call.receiveChannel())
        }.onFailure { e ->
            logger.error(e) { "push icon: write failed source=$source from=$fromAppInstanceId" }
            failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
            return@post
        }

        logger.info { "push icon: stored source=$source from=$fromAppInstanceId" }
        successResponse(call)
    }
}
