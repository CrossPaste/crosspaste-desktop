package com.crosspaste.net.ws

import com.crosspaste.utils.getJsonUtils
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Unified wrapper around Ktor WebSocket sessions (server or client).
 * Provides a simple send/close/isActive API for WsSessionManager.
 *
 * Wire protocol: each logical message is sent as 1 Text frame (JSON header)
 * followed by 0 or 1 Binary frames (payload). A [Mutex] guarantees atomicity
 * for these data-frame pairs so concurrent senders cannot interleave them.
 * Control frames (ping) are single frames and bypass the mutex — RFC 6455
 * permits control frames to be injected between data frames, and Ktor handles
 * pong replies internally so they are invisible to the application stream.
 */
class WsSession(
    private val session: WebSocketSession,
    val remoteAppInstanceId: String,
) {
    private val json = getJsonUtils().JSON
    private val sendMutex = Mutex()

    val isActive: Boolean
        get() = session.isActive

    suspend fun sendEnvelope(envelope: WsEnvelope) {
        sendMutex.withLock {
            session.send(Frame.Text(json.encodeToString(envelope.toHeader())))
            if (envelope.payload.isNotEmpty()) {
                session.send(Frame.Binary(true, envelope.payload))
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
