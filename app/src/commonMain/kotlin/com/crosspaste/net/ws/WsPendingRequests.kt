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
 * A requester [open]s a [Pending] slot, sends an envelope stamped with its
 * [Pending.requestId], then [Pending.await]s the correlated response that the
 * receive loop delivers through [complete]. [request] bundles those steps for
 * callers that only need a boolean send outcome.
 */
internal class WsPendingRequests {

    private val logger = KotlinLogging.logger {}

    private val pending: MutableMap<String, CompletableDeferred<WsEnvelope>> = ConcurrentMap()

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }

    /** One open slot; always [close] it (use `try/finally`) so an unanswered request does not leak. */
    inner class Pending internal constructor(
        val requestId: String,
        private val deferred: CompletableDeferred<WsEnvelope>,
    ) {
        /** Suspends until the correlated response arrives; throws [kotlinx.coroutines.TimeoutCancellationException]. */
        suspend fun await(timeoutMs: Long = DEFAULT_TIMEOUT_MS): WsEnvelope =
            withTimeout(timeoutMs) {
                deferred.await()
            }

        fun close() {
            pending.remove(requestId)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun open(): Pending {
        val requestId = Uuid.random().toString()
        val deferred = CompletableDeferred<WsEnvelope>()
        pending[requestId] = deferred
        return Pending(requestId, deferred)
    }

    /**
     * Send a request envelope and wait for the correlated response.
     *
     * The [envelope] must NOT already have a requestId set — one will be generated.
     * [send] returning false is reported as an [IllegalStateException].
     */
    suspend fun request(
        envelope: WsEnvelope,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        send: suspend (WsEnvelope) -> Boolean,
    ): WsEnvelope {
        val slot = open()
        try {
            if (!send(envelope.copy(requestId = slot.requestId))) {
                throw IllegalStateException("WebSocket request send failed")
            }
            return slot.await(timeoutMs)
        } finally {
            slot.close()
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
        val deferred = pending.remove(requestId)
        if (deferred != null) {
            deferred.complete(response)
            return true
        }
        logger.debug { "No pending request for requestId=$requestId" }
        return false
    }
}
