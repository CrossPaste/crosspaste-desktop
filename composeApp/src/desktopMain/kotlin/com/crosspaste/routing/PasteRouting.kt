package com.crosspaste.routing

import com.crosspaste.CrossPaste
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

fun Routing.pasteRouting() {
    val logger = KotlinLogging.logger {}

    val koinApplication = CrossPaste.koinApplication

    val syncManager = koinApplication.koin.get<SyncManager>()

    val pasteboardService = koinApplication.koin.get<PasteboardService>()

    post("/sync/paste") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.getSyncHandlers()[appInstanceId]?.let { syncHandler ->
                if (!syncHandler.syncRuntimeInfo.allowSend) {
                    logger.debug { "sync handler ($appInstanceId) not allow send" }
                    failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE.toErrorCode())
                    return@post
                }

                val pasteData = call.receive<PasteData>()

                launch {
                    pasteboardService.tryWriteRemotePasteboard(pasteData)
                }
                logger.debug { "sync handler ($appInstanceId) receive pasteData: $pasteData" }
                successResponse(call)
            } ?: run {
                logger.error { "not found appInstance id: $appInstanceId" }
                failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
            }
        }
    }
}
