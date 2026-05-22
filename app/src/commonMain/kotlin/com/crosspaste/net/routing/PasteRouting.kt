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
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger: KLogger = KotlinLogging.logger("PasteRouting")

fun Routing.pasteRouting(
    appControl: AppControl,
    pasteboardService: PasteboardService,
    pasteReleaseService: PasteReleaseService,
    pastePullService: PastePullService,
    pasteRoutingScope: CoroutineScope,
    pushSessionManager: PushSessionManager,
    syncRoutingApi: SyncRoutingApi,
) {
    post("/sync/paste") {
        handleSyncPaste(
            call,
            appControl,
            pasteboardService,
            pasteReleaseService,
            pastePullService,
            pasteRoutingScope,
            pushSessionManager,
            syncRoutingApi,
        )
    }
}

private suspend fun handleSyncPaste(
    call: ApplicationCall,
    appControl: AppControl,
    pasteboardService: PasteboardService,
    pasteReleaseService: PasteReleaseService,
    pastePullService: PastePullService,
    pasteRoutingScope: CoroutineScope,
    pushSessionManager: PushSessionManager,
    syncRoutingApi: SyncRoutingApi,
) {
    val appInstanceId = getAppInstanceId(call) ?: return
    val syncHandler = requireSyncHandler(call, syncRoutingApi, appInstanceId) ?: return

    if (!syncHandler.currentSyncRuntimeInfo.allowReceive) {
        logger.debug { "sync handler ($appInstanceId), user not allow receive" }
        failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_USER.toErrorCode())
        return
    }
    if (!appControl.isReceiveEnabled()) {
        logger.debug { "sync handler ($appInstanceId), app not allow receive" }
        failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP.toErrorCode())
        return
    }

    val mode = call.request.headers[PushHeaders.SYNC_MODE]
    val pasteData = receiveRemotePasteData(call, appInstanceId) ?: return

    if (mode == PushHeaders.SYNC_MODE_PUSH) {
        handlePushPrepare(
            call,
            appInstanceId,
            pasteData,
            appControl,
            pasteReleaseService,
            pastePullService,
            pushSessionManager,
        )
    } else {
        handlePullSync(
            call,
            appInstanceId,
            pasteData,
            appControl,
            pasteboardService,
            pastePullService,
            pasteRoutingScope,
        )
    }
}

private suspend fun receiveRemotePasteData(
    call: ApplicationCall,
    appInstanceId: String,
): PasteData? =
    runCatching {
        call.receive<PasteData>().copy(remote = true)
    }.onFailure { e ->
        logger.error(e) { "sync handler ($appInstanceId) receive pasteData error" }
        val code =
            if (e is PasteException && e.match(StandardErrorCode.DECRYPT_FAIL)) {
                StandardErrorCode.DECRYPT_FAIL
            } else {
                StandardErrorCode.UNKNOWN_ERROR
            }
        failResponse(call, code.toErrorCode())
    }.getOrNull()

/**
 * Permission gating policy: the caller ([handleSyncPaste]) already ran
 * `appControl.isReceiveEnabled()` + `syncHandler.allowReceive`. The push
 * flow's subsequent /sync/file/push and /sync/paste/push/complete trust the
 * session token issued here — toggling permissions mid-session does NOT
 * abort in-flight uploads. The sender learns our decision from this response
 * (200 = go, 4xx = stop).
 */
private suspend fun handlePushPrepare(
    call: ApplicationCall,
    appInstanceId: String,
    pasteData: PasteData,
    appControl: AppControl,
    pasteReleaseService: PasteReleaseService,
    pastePullService: PastePullService,
    pushSessionManager: PushSessionManager,
) {
    if (!pasteData.isFileType()) {
        logger.warn { "push sync: paste from $appInstanceId is not file/image type, rejecting" }
        failResponse(call, StandardErrorCode.PUSH_NOT_FILE_TYPE.toErrorCode())
        return
    }

    val prepared = pasteReleaseService.releaseRemotePasteDataForPush(pasteData)
    if (prepared == null) {
        failResponse(call, StandardErrorCode.PUSH_SESSION_REJECTED.toErrorCode())
        return
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
        return
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
}

private suspend fun handlePullSync(
    call: ApplicationCall,
    appInstanceId: String,
    pasteData: PasteData,
    appControl: AppControl,
    pasteboardService: PasteboardService,
    pastePullService: PastePullService,
    pasteRoutingScope: CoroutineScope,
) {
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
