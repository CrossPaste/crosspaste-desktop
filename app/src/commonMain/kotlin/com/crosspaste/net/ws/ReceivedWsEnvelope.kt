package com.crosspaste.net.ws

import com.crosspaste.utils.getJsonUtils
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel

data class ReceivedWsEnvelope(
    val header: WsEnvelopeHeader,
    val envelope: WsEnvelope,
)

suspend fun receiveWsEnvelope(incoming: ReceiveChannel<Frame>): ReceivedWsEnvelope? {
    val json = getJsonUtils().JSON
    while (true) {
        when (val frame = incoming.receiveCatching().getOrNull() ?: return null) {
            is Frame.Text -> {
                val header = json.decodeFromString<WsEnvelopeHeader>(frame.readText())
                val payload =
                    if (header.hasPayload) {
                        val payloadFrame = incoming.receiveCatching().getOrNull()
                        require(payloadFrame is Frame.Binary) { "Expected binary WebSocket payload" }
                        payloadFrame.readBytes()
                    } else {
                        byteArrayOf()
                    }
                return ReceivedWsEnvelope(
                    header = header,
                    envelope =
                        WsEnvelope(
                            type = header.type,
                            payload = payload,
                            encrypted = header.encrypted,
                            requestId = header.requestId,
                        ),
                )
            }

            is Frame.Close -> return null
            else -> Unit
        }
    }
}
