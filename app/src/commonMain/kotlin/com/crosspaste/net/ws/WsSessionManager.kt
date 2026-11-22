package com.crosspaste.net.ws

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
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
            pendingRequests.failForSession(existing, "WebSocket session replaced for $appInstanceId")
            closeSessionSafely(appInstanceId, existing, "Replaced by new session")
        } else {
            logger.info { "Registered WebSocket session for $appInstanceId" }
        }
        onSessionOpened?.invoke(appInstanceId)
    }

    fun unregisterSession(appInstanceId: String) {
        sessions.remove(appInstanceId)?.let {
            logger.info { "Unregistered WebSocket session for $appInstanceId" }
        }
        pendingRequests.failForPeer(appInstanceId, "WebSocket session unregistered for $appInstanceId")
    }

    fun notifySessionClosed(
        appInstanceId: String,
        session: WsSession,
    ) {
        val removed = sessions.remove(appInstanceId, session)
        pendingRequests.failForSession(session, "WebSocket session closed for $appInstanceId")
        if (removed) {
            logger.info { "Unregistered WebSocket session for $appInstanceId" }
            onSessionClosed?.invoke(appInstanceId)
        }
    }

    fun getSession(appInstanceId: String): WsSession? = sessions[appInstanceId]

    fun isConnected(appInstanceId: String): Boolean = sessions[appInstanceId]?.isActive == true

    fun supportsChunkedPayload(appInstanceId: String): Boolean =
        sessions[appInstanceId]?.peerSupportsChunkedPayload == true

    fun supportsHeartbeatAck(appInstanceId: String): Boolean =
        sessions[appInstanceId]?.let { WsCapability.HEARTBEAT_ACK in it.peerCapabilities } == true

    suspend fun probe(
        appInstanceId: String,
        timeoutMs: Long = WsSession.PING_TIMEOUT_MS,
    ): Boolean {
        val session = sessions[appInstanceId] ?: return false
        if (!session.isActive) {
            notifySessionClosed(appInstanceId, session)
            return false
        }
        val success =
            if (WsCapability.HEARTBEAT_ACK in session.peerCapabilities) {
                if (pendingRequests.hasInFlightForSession(session)) {
                    logger.debug { "Skipping WebSocket heartbeat probe for busy session $appInstanceId" }
                    return true
                }
                probeHeartbeat(appInstanceId, session, timeoutMs)
            } else {
                session.ping()
            }
        if (!success) {
            notifySessionClosed(appInstanceId, session)
            closeSessionSafely(
                appInstanceId = appInstanceId,
                session = session,
                reason = "Heartbeat probe failed",
                timeoutMs = PROBE_CLOSE_TIMEOUT_MS,
            )
        }
        return success
    }

    private suspend fun probeHeartbeat(
        appInstanceId: String,
        session: WsSession,
        timeoutMs: Long,
    ): Boolean {
        val inboundBefore = session.inboundActivitySnapshot()
        val pending = pendingRequests.open(appInstanceId, session)
        try {
            if (sessions[appInstanceId] !== session) return false
            val sent =
                sendToSession(
                    appInstanceId,
                    session,
                    WsEnvelope(type = WsMessageType.HEARTBEAT, requestId = pending.requestId),
                )
            if (!sent) return false

            val response =
                try {
                    pending.await(timeoutMs)
                } catch (_: TimeoutCancellationException) {
                    if (session.inboundActivitySnapshot() != inboundBefore ||
                        pendingRequests.hasInFlightForSession(session, excludeRequestId = pending.requestId)
                    ) {
                        logger.debug {
                            "WebSocket heartbeat probe timed out while session $appInstanceId showed active traffic; keeping session"
                        }
                        return true
                    }
                    logger.warn { "WebSocket heartbeat probe timed out for $appInstanceId" }
                    return false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.debug(e) { "WebSocket heartbeat probe failed for $appInstanceId" }
                    return false
                }
            return response.type == WsMessageType.HEARTBEAT_ACK
        } finally {
            pending.close()
        }
    }

    suspend fun closeSession(appInstanceId: String) {
        val session = sessions.remove(appInstanceId)
        pendingRequests.failForPeer(appInstanceId, "WebSocket session closed for $appInstanceId")
        if (session != null) {
            closeSessionSafely(appInstanceId, session, "Device removed")
        }
    }

    suspend fun closeAll() {
        pendingRequests.failAll("All WebSocket sessions closed")
        try {
            // Drain instead of snapshot-then-iterate: toList() races with concurrent
            // session registration/removal (NoSuchElementException, see #4884).
            while (true) {
                val appInstanceId = sessions.keys.firstOrNull() ?: break
                sessions.remove(appInstanceId)?.let { session ->
                    closeSessionSafely(appInstanceId, session, "App shutting down")
                }
            }
        } finally {
            pendingRequests.failAll("All WebSocket sessions closed")
        }
    }

    private suspend fun closeSessionSafely(
        appInstanceId: String,
        session: WsSession,
        reason: String,
        timeoutMs: Long? = null,
    ) {
        try {
            if (timeoutMs != null) {
                withTimeoutOrNull(timeoutMs) {
                    session.close(reason)
                }
            } else {
                session.close(reason)
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            logger.warn(e) { "WS session already cancelled while closing for $appInstanceId ($reason)" }
        } catch (e: Exception) {
            logger.warn(e) { "Error closing WS session for $appInstanceId ($reason)" }
        }
    }

    suspend fun send(
        appInstanceId: String,
        envelope: WsEnvelope,
    ): Boolean {
        val session = sessions[appInstanceId] ?: return false
        return sendToSession(appInstanceId, session, envelope)
    }

    private suspend fun sendToSession(
        appInstanceId: String,
        session: WsSession,
        envelope: WsEnvelope,
    ): Boolean =
        try {
            session.sendEnvelope(envelope)
            true
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            logger.warn(e) { "WebSocket send cancelled by closed session for $appInstanceId" }
            notifySessionClosed(appInstanceId, session)
            false
        } catch (e: Exception) {
            logger.warn(e) { "WebSocket send failed for $appInstanceId" }
            notifySessionClosed(appInstanceId, session)
            false
        }

    /** Sends [envelope] and suspends until the peer answers with the same requestId. */
    suspend fun request(
        appInstanceId: String,
        envelope: WsEnvelope,
        timeoutMs: Long = WsPendingRequests.DEFAULT_TIMEOUT_MS,
    ): WsEnvelope {
        val session =
            sessions[appInstanceId]
                ?: throw IllegalStateException("WebSocket request send failed: no active session for $appInstanceId")
        return pendingRequests.request(appInstanceId, session, envelope, timeoutMs) { requestEnvelope ->
            sessions[appInstanceId] === session && sendToSession(appInstanceId, session, requestEnvelope)
        }
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

        val pending = pendingRequests.open(appInstanceId, session)
        try {
            if (sessions[appInstanceId] !== session) {
                return WsPayloadSendResult.Failed
            }
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
                } catch (_: TimeoutCancellationException) {
                    logger.warn { "WebSocket paste acknowledgement timed out for $appInstanceId" }
                    return WsPayloadSendResult.Failed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "WebSocket paste acknowledgement failed for $appInstanceId" }
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
            currentCoroutineContext().ensureActive()
            logger.warn(e) { "WebSocket send cancelled by closed session for $appInstanceId" }
            notifySessionClosed(appInstanceId, session)
            WsPayloadSendResult.Failed
        } catch (e: Exception) {
            logger.warn(e) { "WebSocket send failed for $appInstanceId" }
            notifySessionClosed(appInstanceId, session)
            WsPayloadSendResult.Failed
        }

    companion object {
        private const val PROBE_CLOSE_TIMEOUT_MS = 1_000L
    }
}
