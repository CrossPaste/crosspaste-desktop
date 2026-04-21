package com.crosspaste.net.ws

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*

class WsSessionManager {

    private val logger = KotlinLogging.logger {}

    private val sessions: ConcurrentMap<String, WsSession> = ConcurrentMap()

    private var onSessionClosed: ((String) -> Unit)? = null

    fun setOnSessionClosed(callback: (String) -> Unit) {
        onSessionClosed = callback
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
        sessions.keys.toList().forEach { appInstanceId ->
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
}
