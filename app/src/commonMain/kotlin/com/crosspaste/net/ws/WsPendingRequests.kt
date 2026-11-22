package com.crosspaste.net.ws

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class WsSessionClosedException(
    message: String,
) : IllegalStateException(message)

/**
 * Manages in-flight WebSocket request-response correlations.
 *
 * A requester [open]s a [Pending] slot bound to a peer ([appInstanceId]) and
 * optional [WsSession], sends an envelope stamped with its [Pending.requestId],
 * then [Pending.await]s the correlated response that the receive loop delivers
 * through [complete]. When a session closes or is replaced, [failForSession],
 * [failForPeer], or [failAll] immediately fails matching in-flight waiters
 * instead of letting them hang until timeout.
 */
internal class WsPendingRequests {

    private class PendingEntry(
        val appInstanceId: String,
        val session: WsSession?,
        val deferred: CompletableDeferred<WsEnvelope>,
    )

    private val logger = KotlinLogging.logger {}
    private val lock = SynchronizedObject()
    private val pending: MutableMap<String, PendingEntry> = HashMap()

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
            synchronized(lock) {
                pending.remove(requestId)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun open(
        appInstanceId: String,
        session: WsSession? = null,
    ): Pending {
        val requestId = Uuid.random().toString()
        val deferred = CompletableDeferred<WsEnvelope>()
        synchronized(lock) {
            pending[requestId] = PendingEntry(appInstanceId, session, deferred)
        }
        return Pending(requestId, deferred)
    }

    /**
     * Send a request envelope and wait for the correlated response.
     *
     * The [envelope] must NOT already have a requestId set — one will be generated.
     * [send] returning false is reported as an [IllegalStateException].
     */
    suspend fun request(
        appInstanceId: String,
        session: WsSession? = null,
        envelope: WsEnvelope,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        send: suspend (WsEnvelope) -> Boolean,
    ): WsEnvelope {
        val slot = open(appInstanceId, session)
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
        val entry = synchronized(lock) { pending.remove(requestId) }
        if (entry != null) {
            entry.deferred.complete(response)
            return true
        }
        logger.debug { "No pending request for requestId=$requestId" }
        return false
    }

    /**
     * Fail all in-flight requests bound to [appInstanceId].
     *
     * @return number of pending requests failed.
     */
    fun failForPeer(
        appInstanceId: String,
        reason: String,
    ): Int = failMatching(reason) { it.appInstanceId == appInstanceId }

    /**
     * Fail all in-flight requests bound to [session].
     *
     * @return number of pending requests failed.
     */
    fun failForSession(
        session: WsSession,
        reason: String,
    ): Int = failMatching(reason) { it.session === session }

    /**
     * Fail all in-flight requests across all peers.
     *
     * @return number of pending requests failed.
     */
    fun failAll(reason: String): Int = failMatching(reason) { true }

    private fun failMatching(
        reason: String,
        predicate: (PendingEntry) -> Boolean,
    ): Int {
        val toFail =
            synchronized(lock) {
                val matched = mutableListOf<CompletableDeferred<WsEnvelope>>()
                val iterator = pending.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next().value
                    if (predicate(entry)) {
                        iterator.remove()
                        matched.add(entry.deferred)
                    }
                }
                matched
            }
        if (toFail.isNotEmpty()) {
            val cause = WsSessionClosedException(reason)
            toFail.forEach { it.completeExceptionally(cause) }
        }
        return toFail.size
    }
}
