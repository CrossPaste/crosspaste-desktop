package com.crosspaste.net.ws

import com.crosspaste.sync.SyncManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.launch

class WsSessionManager(
    private val lazySyncManager: Lazy<SyncManager>,
) {
    private val logger = KotlinLogging.logger {}

    private val sessions: MutableMap<String, WsSession> = ConcurrentMap()

    fun registerSession(
        appInstanceId: String,
        session: WsSession,
    ) {
        val existing = sessions.put(appInstanceId, session)
        if (existing != null) {
            logger.info { "Replaced existing WebSocket session for $appInstanceId" }
        } else {
            logger.info { "Registered WebSocket session for $appInstanceId" }
        }
    }

    fun unregisterSession(appInstanceId: String) {
        sessions.remove(appInstanceId)?.let {
            logger.info { "Unregistered WebSocket session for $appInstanceId" }
        }
    }

    fun getSession(appInstanceId: String): WsSession? = sessions[appInstanceId]

    fun isConnected(appInstanceId: String): Boolean = sessions[appInstanceId]?.isActive == true

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
            unregisterSession(appInstanceId)
            onSessionClosed(appInstanceId)
        }.getOrDefault(false)
    }

    fun notifySessionClosed(appInstanceId: String) {
        unregisterSession(appInstanceId)
        onSessionClosed(appInstanceId)
    }

    private fun onSessionClosed(appInstanceId: String) {
        val syncManager = lazySyncManager.value
        syncManager.getSyncHandler(appInstanceId)?.let { handler ->
            syncManager.realTimeSyncScope.launch {
                handler.forceResolve()
            }
        }
    }

    fun getAllSessions(): Map<String, WsSession> = sessions.toMap()
}
