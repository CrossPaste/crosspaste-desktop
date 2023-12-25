package com.clipevery.utils

import com.clipevery.app.AppInfo
import com.clipevery.exception.StandardErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore


suspend fun successResponse(call: ApplicationCall) {
    call.respond(status = HttpStatusCode.OK, message = "")
}

suspend fun failResponse(call: ApplicationCall, message: FailResponse) {
    call.respond(status = HttpStatusCode.InternalServerError, message = message)
}

@ExperimentalSerializationApi
suspend inline fun <reified T : Any> decodeReceive(call: ApplicationCall,
                                                   signalProtocolStore: SignalProtocolStore): T {
    val bytes = call.receive<ByteArray>()
    val appInstanceId = call.request.headers["appInstanceId"]
    if (appInstanceId == null) {
        failResponse(call, FailResponse(StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode().code,
            "not found app instance id"))
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

data class FailResponse(val errorCode: Int,
                        val message: String)