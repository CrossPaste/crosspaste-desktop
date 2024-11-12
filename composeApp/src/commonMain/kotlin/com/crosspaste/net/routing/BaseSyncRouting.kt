package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Routing.baseSyncRouting(
    appInfo: AppInfo,
    endpointInfoFactory: EndpointInfoFactory,
    secureStore: SecureStore,
    syncManager: SyncManager,
) {
    val logger = KotlinLogging.logger {}

    get("/sync/telnet") {
        successResponse(call)
    }

    get("/sync/syncInfo") {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        successResponse(call, syncInfo)
    }

    post("/sync/heartbeat") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }

            try {
                val syncInfo = call.receive(SyncInfo::class)
                // todo check diff time to update
                syncManager.updateSyncInfo(syncInfo)
                logger.debug { "$appInstanceId heartbeat to ${appInfo.appInstanceId} success" }
                successResponse(call)
            } catch (e: Exception) {
                logger.error(e) { "$appInstanceId heartbeat to ${appInfo.appInstanceId} fail" }
                failResponse(call, StandardErrorCode.SIGN_INVALID.toErrorCode())
            }
        }
    }

    get("/sync/notifyExit") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.markExit(appInstanceId)
            successResponse(call)
        }
    }

    get("/sync/notifyRemove") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.removeSyncHandler(appInstanceId)
            successResponse(call)
        }
    }
}
