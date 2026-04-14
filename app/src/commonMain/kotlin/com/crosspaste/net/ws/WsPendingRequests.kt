package com.crosspaste.net.ws

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manages in-flight WebSocket request-response correlations.
 *
 * A requester calls [request] which generates a unique [requestId],
 * sends the envelope, and suspends until a matching response arrives
 * via [complete] or the timeout elapses.
 */
class WsPendingRequests {

    private val logger = KotlinLogging.logger {}

    private val pending: MutableMap<String, CompletableDeferred<WsEnvelope>> = ConcurrentMap()

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }

    /**
     * Send a request envelope and wait for the correlated response.
     *
     * The [envelope] must NOT already have a requestId set — one will be generated.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun request(
        wsSessionManager: WsSessionManager,
        appInstanceId: String,
        envelope: WsEnvelope,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): WsEnvelope {
        val requestId = Uuid.random().toString()
        val deferred = CompletableDeferred<WsEnvelope>()
        pending[requestId] = deferred

        val requestEnvelope =
            envelope.copy(requestId = requestId)

        try {
            val sent = wsSessionManager.send(appInstanceId, requestEnvelope)
            if (!sent) {
                throw IllegalStateException("WebSocket send failed for $appInstanceId")
            }
            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            pending.remove(requestId)
        }
    }

    /**
     * Complete a pending request with a response envelope.
     *
     * @return true if a matching pending request was found and completed.
     */
    fun complete(
        requestId: String,
        response: WsEnvelope,
    ): Boolean {
        val deferred = pending[requestId]
        if (deferred != null) {
            deferred.complete(response)
            return true
        }
        logger.debug { "No pending request for requestId=$requestId" }
        return false
    }
}
