package com.crosspaste.net.ws

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

class WsSessionManager {

    private val logger = KotlinLogging.logger {}
    private val pendingRequests = WsPendingRequests()

    private val sessions: ConcurrentMap<String, WsSession> = ConcurrentMap()

    private var onSessionClosed: ((String) -> Unit)? = null

    private var onSessionOpened: ((String) -> Unit)? = null

    fun setOnSessionClosed(callback: (String) -> Unit) {
        onSessionClosed = callback
    }

    fun setOnSessionOpened(callback: (String) -> Unit) {
        onSessionOpened = callback
    }

    suspend fun registerSession(
        appInstanceId: String,
        session: WsSession,
    ) {
        val existing = sessions.put(appInstanceId, session)
        if (existing != null) {
            logger.info { "Replaced existing WebSocket session for $appInstanceId" }
            runCatching { existing.close("Replaced by new session") }
                .onFailure { e -> logger.warn(e) { "Error closing old WS session for $appInstanceId" } }
        } else {
            logger.info { "Registered WebSocket session for $appInstanceId" }
        }
        onSessionOpened?.invoke(appInstanceId)
    }

    fun unregisterSession(appInstanceId: String) {
        sessions.remove(appInstanceId)?.let {
            logger.info { "Unregistered WebSocket session for $appInstanceId" }
        }
    }

    fun notifySessionClosed(
        appInstanceId: String,
        session: WsSession,
    ) {
        if (sessions.remove(appInstanceId, session)) {
            logger.info { "Unregistered WebSocket session for $appInstanceId" }
            onSessionClosed?.invoke(appInstanceId)
        }
    }

    fun getSession(appInstanceId: String): WsSession? = sessions[appInstanceId]

    fun isConnected(appInstanceId: String): Boolean = sessions[appInstanceId]?.isActive == true

    fun supportsChunkedPayload(appInstanceId: String): Boolean =
        sessions[appInstanceId]?.peerSupportsChunkedPayload == true

    suspend fun probe(appInstanceId: String): Boolean {
        val session = sessions[appInstanceId] ?: return false
        if (!session.isActive) {
            notifySessionClosed(appInstanceId, session)
            return false
        }
        val success = session.ping()
        if (!success) {
            notifySessionClosed(appInstanceId, session)
        }
        return success
    }

    suspend fun closeSession(appInstanceId: String) {
        sessions.remove(appInstanceId)?.let { session ->
            runCatching { session.close("Device removed") }
                .onFailure { e -> logger.warn(e) { "Error closing WS session for $appInstanceId" } }
        }
    }

    suspend fun closeAll() {
        // Drain instead of snapshot-then-iterate: toList() races with concurrent
        // session registration/removal (NoSuchElementException, see #4884).
        while (true) {
            val appInstanceId = sessions.keys.firstOrNull() ?: break
            sessions.remove(appInstanceId)?.let { session ->
                runCatching { session.close("App shutting down") }
                    .onFailure { e -> logger.warn(e) { "Error closing WS session for $appInstanceId" } }
            }
        }
    }

    suspend fun send(
        appInstanceId: String,
        envelope: WsEnvelope,
    ): Boolean {
        val session = sessions[appInstanceId] ?: return false
        return runCatching {
            session.sendEnvelope(envelope)
            true
        }.onFailure { e ->
            logger.warn(e) { "WebSocket send failed for $appInstanceId" }
            notifySessionClosed(appInstanceId, session)
        }.getOrDefault(false)
    }

    /** Sends [envelope] and suspends until the peer answers with the same requestId. */
    suspend fun request(
        appInstanceId: String,
        envelope: WsEnvelope,
        timeoutMs: Long = WsPendingRequests.DEFAULT_TIMEOUT_MS,
    ): WsEnvelope =
        pendingRequests.request(envelope, timeoutMs) { requestEnvelope ->
            send(appInstanceId, requestEnvelope)
        }

    internal fun completePendingRequest(
        requestId: String,
        response: WsEnvelope,
    ): Boolean = pendingRequests.complete(requestId, response)

    /**
     * Sends a paste over one stable session snapshot, so a reconnect cannot
     * swap in a session with different capabilities between the capability
     * checks and the send. Peers that advertise [WsCapability.PASTE_PUSH_ACK]
     * must confirm ingestion before this reports [WsPayloadSendResult.Sent];
     * legacy peers keep the historical write-only behavior.
     */
    suspend fun sendPastePush(
        appInstanceId: String,
        envelope: WsEnvelope,
        singleFramePayloadLimit: Long,
        chunkedPayloadLimit: Long,
        ackTimeoutMs: Long = WsPendingRequests.DEFAULT_TIMEOUT_MS,
    ): WsPayloadSendResult {
        val session = sessions[appInstanceId] ?: return WsPayloadSendResult.Failed
        if (WsCapability.PASTE_PUSH_ACK !in session.peerCapabilities) {
            return sendWithPayloadLimits(session, appInstanceId, envelope, singleFramePayloadLimit, chunkedPayloadLimit)
        }

        val pending = pendingRequests.open()
        try {
            val sendResult =
                sendWithPayloadLimits(
                    session,
                    appInstanceId,
                    envelope.copy(requestId = pending.requestId),
                    singleFramePayloadLimit,
                    chunkedPayloadLimit,
                )
            if (sendResult != WsPayloadSendResult.Sent) return sendResult

            val response =
                try {
                    pending.await(ackTimeoutMs)
                } catch (e: TimeoutCancellationException) {
                    logger.warn { "WebSocket paste acknowledgement timed out for $appInstanceId" }
                    return WsPayloadSendResult.Failed
                }
            return when (response.type) {
                WsMessageType.PASTE_PUSH_ACK -> WsPayloadSendResult.Sent
                WsMessageType.ERROR -> {
                    val detail = response.payload.decodeToString().ifEmpty { "no detail" }
                    logger.warn { "WebSocket paste rejected by $appInstanceId: $detail" }
                    WsPayloadSendResult.Rejected(detail)
                }
                else -> {
                    logger.warn { "Unexpected WebSocket paste response from $appInstanceId: ${response.type}" }
                    WsPayloadSendResult.Failed
                }
            }
        } finally {
            pending.close()
        }
    }

    private suspend fun sendWithPayloadLimits(
        session: WsSession,
        appInstanceId: String,
        envelope: WsEnvelope,
        singleFramePayloadLimit: Long,
        chunkedPayloadLimit: Long,
    ): WsPayloadSendResult =
        try {
            session.sendEnvelopeWithPayloadLimits(
                envelope = envelope,
                singleFramePayloadLimit = singleFramePayloadLimit,
                chunkedPayloadLimit = chunkedPayloadLimit,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "WebSocket send failed for $appInstanceId" }
            notifySessionClosed(appInstanceId, session)
            WsPayloadSendResult.Failed
        }
}
