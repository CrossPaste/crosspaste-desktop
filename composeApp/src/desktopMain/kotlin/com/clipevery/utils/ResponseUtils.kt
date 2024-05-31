package com.clipevery.utils

import com.clipevery.exception.ErrorCode
import com.clipevery.exception.ErrorType
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.clientapi.FailResponse
import io.ktor.http.*
import io.ktor.http.ContentType.Application.OctetStream
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*

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
    val appInstanceId = call.request.headers["appInstanceId"]
    if (appInstanceId == null) {
        failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
    }
    return appInstanceId
}
