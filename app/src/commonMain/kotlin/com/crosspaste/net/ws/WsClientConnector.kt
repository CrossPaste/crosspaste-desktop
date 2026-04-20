package com.crosspaste.net.ws

import com.crosspaste.app.AppInfo
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch

class WsClientConnector(
    private val appInfo: AppInfo,
    private val client: HttpClient,
    private val wsSessionManager: WsSessionManager,
    private val wsMessageHandler: WsMessageHandler,
) {
    private val logger = KotlinLogging.logger {}
    private val json = getJsonUtils().JSON
    private val connectScope = namedScope(ioDispatcher, "WsClientConnector")

    /**
     * Attempt to open a WebSocket connection to a remote peer.
     * This is called opportunistically after HTTP CONNECTED state is reached.
     * Returns true if the WebSocket was successfully established.
     */
    suspend fun connect(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ): Boolean =
        runCatching {
            val path = "/ws/sync?appInstanceId=${appInfo.appInstanceId}&targetAppInstanceId=$targetAppInstanceId"

            client.webSocket(host = host, port = port, path = path) {
                logger.info { "WebSocket client connected to $targetAppInstanceId at $host:$port" }
                val wsSession = WsSession(this, targetAppInstanceId)
                wsSessionManager.registerSession(targetAppInstanceId, wsSession)

                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                runCatching {
                                    val header = json.decodeFromString<WsEnvelopeHeader>(frame.readText())
                                    val payload =
                                        if (header.hasPayload) {
                                            (incoming.receive() as Frame.Binary).readBytes()
                                        } else {
                                            byteArrayOf()
                                        }
                                    val envelope =
                                        WsEnvelope(
                                            type = header.type,
                                            payload = payload,
                                            encrypted = header.encrypted,
                                            requestId = header.requestId,
                                        )
                                    wsMessageHandler.handleMessage(targetAppInstanceId, envelope)
                                }.onFailure { e ->
                                    logger.error(e) { "Failed to handle WS message from $targetAppInstanceId" }
                                }
                            }

                            is Frame.Close -> {
                                logger.info { "WebSocket close frame from $targetAppInstanceId" }
                            }

                            else -> {
                                logger.debug { "Ignoring non-text WS frame from $targetAppInstanceId" }
                            }
                        }
                    }
                } finally {
                    logger.info { "WebSocket client disconnected from $targetAppInstanceId" }
                    wsSessionManager.notifySessionClosed(targetAppInstanceId)
                }
            }
            true
        }.onFailure { e ->
            logger.debug(e) { "WebSocket connection to $targetAppInstanceId at $host:$port failed (fallback to HTTP)" }
        }.getOrDefault(false)

    /**
     * Attempt WebSocket connection in the background.
     * Does not block — failures are silently handled.
     */
    fun connectAsync(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ) {
        connectScope.launch {
            connect(host, port, targetAppInstanceId)
        }
    }
}
