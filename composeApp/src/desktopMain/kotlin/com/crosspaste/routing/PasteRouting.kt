package com.crosspaste.routing

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.PasteboardService
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch

fun Routing.pasteRouting(
    syncManager: SyncManager,
    pasteboardService: PasteboardService,
) {
    val logger = KotlinLogging.logger {}

    post("/sync/paste") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.getSyncHandlers()[appInstanceId]?.let { syncHandler ->
                if (!syncHandler.syncRuntimeInfo.allowReceive) {
                    logger.debug { "sync handler ($appInstanceId) not allow receive" }
                    failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE.toErrorCode())
                    return@post
                }

                try {
                    val pasteData = call.receive<PasteData>()

                    launch {
                        pasteboardService.tryWriteRemotePasteboard(pasteData)
                    }
                    logger.debug { "sync handler ($appInstanceId) receive pasteData: $pasteData" }
                    successResponse(call)
                } catch (e: Exception) {
                    logger.error(e) { "sync handler ($appInstanceId) receive pasteData error" }
                    failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
                    return@post
                }
            } ?: run {
                logger.error { "not found appInstance id: $appInstanceId" }
                failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
            }
        }
    }
}
