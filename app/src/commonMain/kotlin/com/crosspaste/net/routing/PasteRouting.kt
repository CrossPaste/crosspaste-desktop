package com.crosspaste.net.routing

import com.crosspaste.app.AppControl
import com.crosspaste.dto.push.PushHeaders
import com.crosspaste.dto.push.PushPrepareResponse
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.sync.PastePullService
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.requireSyncHandler
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Routing.pasteRouting(
    appControl: AppControl,
    pasteboardService: PasteboardService,
    pasteReleaseService: PasteReleaseService,
    pastePullService: PastePullService,
    pasteRoutingScope: CoroutineScope,
    pushSessionManager: PushSessionManager,
    syncRoutingApi: SyncRoutingApi,
) {
    val logger = KotlinLogging.logger {}

    post("/sync/paste") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val syncHandler = requireSyncHandler(call, syncRoutingApi, appInstanceId) ?: return@let

            if (!syncHandler.currentSyncRuntimeInfo.allowReceive) {
                logger.debug { "sync handler ($appInstanceId), user not allow receive" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_USER.toErrorCode())
                return@let
            }

            if (!appControl.isReceiveEnabled()) {
                logger.debug { "sync handler  $appInstanceId, app not allow receive" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP.toErrorCode())
                return@let
            }

            val mode = call.request.headers[PushHeaders.SYNC_MODE]
            val pasteData =
                runCatching {
                    call.receive<PasteData>().copy(remote = true)
                }.onFailure { e ->
                    logger.error(e) { "sync handler ($appInstanceId) receive pasteData error" }
                    if (e is PasteException && e.match(StandardErrorCode.DECRYPT_FAIL)) {
                        failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
                    } else {
                        failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
                    }
                }.getOrNull() ?: return@let

            if (mode == PushHeaders.SYNC_MODE_PUSH) {
                // Permission gating policy: this branch is the ONLY place we check
                // appControl.isReceiveEnabled() + syncHandler.allowReceive for the
                // push flow. The two checks already ran above before we got here.
                // Subsequent /sync/file/push and /sync/paste/push/complete trust the
                // session token issued in our prepare response — toggling permissions
                // mid-session does NOT abort in-flight uploads. The sender learns
                // our decision from this response (200 = go, 4xx = stop).
                if (!pasteData.isFileType()) {
                    logger.warn { "push sync: paste from $appInstanceId is not file/image type, rejecting" }
                    failResponse(call, StandardErrorCode.PUSH_NOT_FILE_TYPE.toErrorCode())
                    return@let
                }

                val prepared = pasteReleaseService.releaseRemotePasteDataForPush(pasteData)
                if (prepared == null) {
                    failResponse(call, StandardErrorCode.PUSH_SESSION_REJECTED.toErrorCode())
                    return@let
                }

                val session =
                    pushSessionManager.create(
                        pasteId = prepared.pasteId,
                        fromAppInstanceId = appInstanceId,
                        filesIndex = prepared.filesIndex,
                    )
                if (session == null) {
                    logger.warn { "push sync: rejected pasteId=${prepared.pasteId} (capacity)" }
                    failResponse(call, StandardErrorCode.PUSH_SESSION_REJECTED.toErrorCode())
                    return@let
                }

                pastePullService.updateMaxCreateTime(appInstanceId, pasteData.createTime)
                appControl.completeReceiveOperation()
                logger.debug {
                    "sync handler ($appInstanceId) push prepare ok: pasteId=${prepared.pasteId} " +
                        "chunkCount=${prepared.chunkCount}"
                }
                successResponse(
                    call,
                    PushPrepareResponse(
                        pasteId = prepared.pasteId,
                        chunkCount = prepared.chunkCount,
                        chunkSize = prepared.chunkSize,
                        sessionToken = session.token,
                        needIcon = prepared.needIcon,
                    ),
                )
                return@let
            }

            pasteRoutingScope.launch {
                pasteboardService
                    .tryWriteRemotePasteboard(pasteData)
                    .onSuccess {
                        pastePullService.updateMaxCreateTime(appInstanceId, pasteData.createTime)
                    }
            }
            logger.debug { "sync handler ($appInstanceId) receive pasteData: $pasteData" }
            appControl.completeReceiveOperation()
            successResponse(call)
        }
    }
}
