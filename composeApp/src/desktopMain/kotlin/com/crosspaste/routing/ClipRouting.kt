package com.crosspaste.routing

import com.crosspaste.CrossPaste
import com.crosspaste.clip.ClipboardService
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch

fun Routing.clipRouting() {
    val logger = KotlinLogging.logger {}

    val koinApplication = CrossPaste.koinApplication

    val syncManager = koinApplication.koin.get<SyncManager>()

    val clipboardService = koinApplication.koin.get<ClipboardService>()

    post("/sync/clip") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.getSyncHandlers()[appInstanceId]?.let { syncHandler ->
                if (!syncHandler.syncRuntimeInfo.allowSend) {
                    logger.debug { "sync handler ($appInstanceId) not allow send" }
                    failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE.toErrorCode())
                    return@post
                }

                val clipData = call.receive<ClipData>()

                launch {
                    clipboardService.tryWriteRemoteClipboard(clipData)
                }
                logger.debug { "sync handler ($appInstanceId) receive clipData: $clipData" }
                successResponse(call)
            } ?: run {
                logger.error { "not found appInstance id: $appInstanceId" }
                failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
            }
        }
    }
}
