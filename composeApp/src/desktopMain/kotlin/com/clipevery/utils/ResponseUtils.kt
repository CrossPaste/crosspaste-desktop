package com.clipevery.utils

import com.clipevery.app.AppInfo
import com.clipevery.exception.ErrorCode
import com.clipevery.exception.ErrorType
import com.clipevery.exception.StandardErrorCode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore


suspend inline fun successResponse(call: ApplicationCall) {
    call.respond(status = HttpStatusCode.OK, message = "")
}

suspend inline fun successResponse(call: ApplicationCall, message: ByteArray) {
    call.response.header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
    call.respondBytes(message)
}

suspend inline fun <reified T : Any> successResponse(call: ApplicationCall, message: T) {
    call.respond(status = HttpStatusCode.OK, message = message)
}

suspend inline fun failResponse(call: ApplicationCall, message: FailResponse, status: HttpStatusCode = HttpStatusCode.InternalServerError) {
    call.respond(status = status, message = message)
}

suspend inline fun failResponse(call: ApplicationCall, errorCode: ErrorCode, message: String? = null) {
    val code = errorCode.code
    val type = errorCode.type
    val status = when (type) {
        ErrorType.EXTERNAL_ERROR -> HttpStatusCode.BadRequest
        ErrorType.INTERNAL_ERROR -> HttpStatusCode.InternalServerError
        ErrorType.USER_ERROR -> HttpStatusCode.UnprocessableEntity
    }
    val failMessage = FailResponse(code, errorCode.name)
    failResponse(call, failMessage, status)
}

@ExperimentalSerializationApi
suspend inline fun <reified T : Any> decodeReceive(call: ApplicationCall,
                                                   signalProtocolStore: SignalProtocolStore): T {
    val bytes = call.receive<ByteArray>()
    val appInstanceId = call.request.headers["appInstanceId"]
    if (appInstanceId == null) {
        failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
    }
    val signalProtocolAddress = SignalProtocolAddress(appInstanceId!!, 1)

    val signalMessage = SignalMessage(bytes)

    val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    val decrypt = sessionCipher.decrypt(signalMessage)

    return Json.decodeFromStream(decrypt.inputStream())
}

suspend inline fun <reified T : Any> encodeResponse(call: ApplicationCall,
                                                    appInfo: AppInfo,
                                                    signalProtocolStore: SignalProtocolStore,
                                                    message: T) {
    call.response.header("appInstanceId", appInfo.appInstanceId)

    val signalProtocolAddress = SignalProtocolAddress(appInfo.appInstanceId, 1)

    val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    val encrypt = sessionCipher.encrypt(Json.encodeToString(message).encodeToByteArray())

    call.respond(status = HttpStatusCode.OK, message = encrypt)
}

suspend inline fun getAppInstanceId(call: ApplicationCall): String? {
    val appInstanceId = call.request.headers["appInstanceId"]
    if (appInstanceId == null) {
        failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
    }
    return appInstanceId
}

@Serializable
data class FailResponse(val errorCode: Int,
                        val message: String = "")