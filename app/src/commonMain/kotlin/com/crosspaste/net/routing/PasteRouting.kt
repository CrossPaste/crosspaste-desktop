package com.crosspaste.net.routing

import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteData
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

fun Routing.pasteRouting(
    appControl: AppControl,
    pasteboardService: PasteboardService,
    syncRoutingApi: SyncRoutingApi,
) {
    val logger = KotlinLogging.logger {}

    val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    post("/sync/paste") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val syncHandler =
                syncRoutingApi.getSyncHandler(appInstanceId) ?: run {
                    logger.error { "not found appInstance id: $appInstanceId" }
                    failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
                    return@let
                }

            if (!syncHandler.syncRuntimeInfo.allowReceive) {
                logger.debug { "sync handler ($appInstanceId), user not allow receive" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_USER.toErrorCode())
                return@let
            }

            if (!appControl.isReceiveEnabled()) {
                logger.debug { "sync handler  $appInstanceId, app not allow receive" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP.toErrorCode())
                return@let
            }

            try {
                val pasteData = call.receive<PasteData>()

                scope.launch {
                    pasteboardService.tryWriteRemotePasteboard(
                        pasteData,
                    )
                }
                logger.debug { "sync handler ($appInstanceId) receive pasteData: $pasteData" }
                appControl.completeReceiveOperation()
                successResponse(call)
            } catch (e: Exception) {
                logger.error(e) { "sync handler ($appInstanceId) receive pasteData error" }
                failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
            }
        }
    }
}
