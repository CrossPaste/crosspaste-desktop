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
                        receiveChunkedPayload(incoming, header.payloadChunkCount)
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

private suspend fun receiveChunkedPayload(
    incoming: ReceiveChannel<Frame>,
    chunkCount: Int,
): ByteArray {
    require(chunkCount in 1..WS_MAX_PAYLOAD_CHUNK_COUNT) {
        "Invalid WebSocket payload chunk count: $chunkCount"
    }
    val chunks = ArrayList<ByteArray>(chunkCount)
    var totalSize = 0L
    repeat(chunkCount) {
        val payloadFrame = incoming.receiveCatching().getOrNull()
        require(payloadFrame is Frame.Binary) { "Expected binary WebSocket payload" }
        val bytes = payloadFrame.readBytes()
        totalSize += bytes.size
        require(totalSize <= WS_MAX_PAYLOAD_SIZE) {
            "WebSocket payload exceeds $WS_MAX_PAYLOAD_SIZE bytes"
        }
        chunks.add(bytes)
    }
    if (chunks.size == 1) return chunks[0]
    val payload = ByteArray(totalSize.toInt())
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(payload, offset)
        offset += chunk.size
    }
    return payload
}
