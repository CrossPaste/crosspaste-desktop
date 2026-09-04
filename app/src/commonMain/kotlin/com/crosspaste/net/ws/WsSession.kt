package com.crosspaste.net.ws

import com.crosspaste.utils.getJsonUtils
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

sealed interface WsPayloadSendResult {

    data object Sent : WsPayloadSendResult

    data object Failed : WsPayloadSendResult

    /** The peer received the payload and explicitly refused it; retrying cannot help. */
    data class Rejected(
        val detail: String,
    ) : WsPayloadSendResult

    data class PayloadTooLarge(
        val payloadSize: Int,
        val payloadLimit: Long,
    ) : WsPayloadSendResult
}

/**
 * Unified wrapper around Ktor WebSocket sessions (server or client).
 * Provides a simple send/close/isActive API for WsSessionManager.
 *
 * Wire protocol: each logical message is sent as 1 Text frame (JSON header)
 * followed by 0..n Binary frames (payload). Payloads larger than
 * [WS_PAYLOAD_CHUNK_SIZE] are split into multiple Binary frames only when the
 * peer advertised support during the authentication handshake
 * ([peerSupportsChunkedPayload]); otherwise the payload is always a single
 * frame. A [Mutex] guarantees atomicity for these data-frame sequences so
 * concurrent senders cannot interleave them. Control frames (ping) are single
 * frames and bypass the mutex — RFC 6455 permits control frames to be injected
 * between data frames, and Ktor handles pong replies internally so they are
 * invisible to the application stream.
 */
class WsSession(
    private val session: WebSocketSession,
    val remoteAppInstanceId: String,
    private val authenticationContext: WsAuthenticationContext? = null,
    val peerSupportsChunkedPayload: Boolean = false,
    val peerCapabilities: Set<String> = emptySet(),
) {
    private val json = getJsonUtils().JSON
    private val sendMutex = Mutex()

    val isActive: Boolean
        get() = session.isActive

    suspend fun sendEnvelope(envelope: WsEnvelope) {
        sendMutex.withLock {
            sendEnvelopeLocked(envelope)
        }
    }

    /** Selects the negotiated limit and sends while holding this session's send lock. */
    suspend fun sendEnvelopeWithPayloadLimits(
        envelope: WsEnvelope,
        singleFramePayloadLimit: Long,
        chunkedPayloadLimit: Long,
    ): WsPayloadSendResult =
        sendMutex.withLock {
            val payloadLimit =
                if (peerSupportsChunkedPayload) {
                    chunkedPayloadLimit
                } else {
                    singleFramePayloadLimit
                }
            if (envelope.payload.size > payloadLimit) {
                return@withLock WsPayloadSendResult.PayloadTooLarge(envelope.payload.size, payloadLimit)
            }
            sendEnvelopeLocked(envelope)
            WsPayloadSendResult.Sent
        }

    private suspend fun sendEnvelopeLocked(envelope: WsEnvelope) {
        val payload = envelope.payload
        val chunkCount =
            if (peerSupportsChunkedPayload && payload.size > WS_PAYLOAD_CHUNK_SIZE) {
                (payload.size + WS_PAYLOAD_CHUNK_SIZE - 1) / WS_PAYLOAD_CHUNK_SIZE
            } else {
                1
            }
        val baseHeader = authenticationContext?.createHeader(envelope) ?: envelope.toHeader()
        val header =
            if (chunkCount > 1) baseHeader.copy(payloadChunkCount = chunkCount) else baseHeader
        session.send(Frame.Text(json.encodeToString(header)))
        if (payload.isEmpty()) return
        if (chunkCount == 1) {
            session.send(Frame.Binary(true, payload))
        } else {
            var offset = 0
            while (offset < payload.size) {
                val end = minOf(offset + WS_PAYLOAD_CHUNK_SIZE, payload.size)
                session.send(Frame.Binary(true, payload.copyOfRange(offset, end)))
                offset = end
            }
        }
    }

    suspend fun ping(): Boolean =
        withTimeoutOrNull(PING_TIMEOUT_MS) {
            try {
                session.send(Frame.Ping(PING_PAYLOAD))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        } ?: false

    suspend fun close(reason: String = "Normal closure") {
        session.close(CloseReason(CloseReason.Codes.NORMAL, reason))
    }

    companion object {
        private val PING_PAYLOAD = "cp".encodeToByteArray()
        private const val PING_TIMEOUT_MS = 3_000L
    }
}
