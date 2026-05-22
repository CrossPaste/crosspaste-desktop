package com.crosspaste.utils

import com.crosspaste.app.AppInfo
import com.crosspaste.exception.ErrorCode
import com.crosspaste.exception.ErrorType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.FailResponse
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.sync.SyncHandler
import io.ktor.http.*
import io.ktor.http.ContentType.Application.OctetStream
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*

const val HEADER_APP_INSTANCE_ID: String = "appInstanceId"
const val HEADER_TARGET_APP_INSTANCE_ID: String = "targetAppInstanceId"

suspend inline fun successResponse(call: ApplicationCall) {
    call.respond(status = HttpStatusCode.OK, message = "")
}

suspend inline fun <reified T : Any> successResponse(
    call: ApplicationCall,
    message: T,
) {
    call.respond(status = HttpStatusCode.OK, message = message)
}

suspend inline fun successResponse(
    call: ApplicationCall,
    noinline producer: suspend ByteWriteChannel.() -> Unit,
) {
    call.respondBytesWriter(contentType = OctetStream, status = HttpStatusCode.OK, producer = producer)
}

suspend inline fun failResponse(
    call: ApplicationCall,
    message: FailResponse,
    status: HttpStatusCode = HttpStatusCode.InternalServerError,
) {
    call.respond(status = status, message = message)
}

suspend inline fun failResponse(
    call: ApplicationCall,
    errorCode: ErrorCode,
    message: String? = null,
) {
    val code = errorCode.code
    val type = errorCode.type
    val status =
        when (type) {
            ErrorType.EXTERNAL_ERROR -> HttpStatusCode.BadRequest
            ErrorType.INTERNAL_ERROR -> HttpStatusCode.InternalServerError
            ErrorType.USER_ERROR -> HttpStatusCode.UnprocessableEntity
        }
    val failMessage = FailResponse(code, message ?: errorCode.name)
    failResponse(call, failMessage, status)
}

suspend inline fun getAppInstanceId(call: ApplicationCall): String? {
    val appInstanceId = call.request.headers[HEADER_APP_INSTANCE_ID]
    if (appInstanceId == null) {
        failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
    }
    return appInstanceId
}

/**
 * Verifies the `targetAppInstanceId` header on the inbound call matches this
 * instance. Writes a [StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID] 4xx and
 * returns false on mismatch.
 */
suspend fun requireTargetAppInstance(
    call: ApplicationCall,
    appInfo: AppInfo,
): Boolean {
    val target = call.request.headers[HEADER_TARGET_APP_INSTANCE_ID]
    if (target != appInfo.appInstanceId) {
        failResponse(call, StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
        return false
    }
    return true
}

/**
 * Resolves the [SyncHandler] for [fromAppInstanceId]. Writes a
 * [StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID] 4xx and returns null when the
 * peer is unknown (e.g. removed mid-session). Callers should consult its
 * `allowReceive`/`allowSend` flags themselves where applicable.
 */
suspend fun requireSyncHandler(
    call: ApplicationCall,
    syncRoutingApi: SyncRoutingApi,
    fromAppInstanceId: String,
): SyncHandler? =
    syncRoutingApi.getSyncHandler(fromAppInstanceId) ?: run {
        failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
        null
    }
