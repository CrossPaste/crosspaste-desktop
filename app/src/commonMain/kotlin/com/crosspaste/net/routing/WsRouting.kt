package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.net.ws.WsEnvelope
import com.crosspaste.net.ws.WsEnvelopeHeader
import com.crosspaste.net.ws.WsMessageHandler
import com.crosspaste.net.ws.WsSession
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun Routing.wsRouting(
    appInfo: AppInfo,
    secureStore: SecureStore,
    wsSessionManager: WsSessionManager,
    wsMessageHandler: WsMessageHandler,
) {
    val logger = KotlinLogging.logger {}
    val json = getJsonUtils().JSON

    webSocket("/ws/sync") {
        val appInstanceId = call.request.queryParameters["appInstanceId"]
        val targetAppInstanceId = call.request.queryParameters["targetAppInstanceId"]

        if (appInstanceId == null || targetAppInstanceId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing appInstanceId or targetAppInstanceId"))
            return@webSocket
        }

        if (targetAppInstanceId != appInfo.appInstanceId) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "targetAppInstanceId mismatch"))
            return@webSocket
        }

        if (!secureStore.existCryptPublicKey(appInstanceId)) {
            logger.warn { "WS connection rejected: $appInstanceId not paired" }
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not paired"))
            return@webSocket
        }

        logger.info { "WebSocket connected: $appInstanceId → ${appInfo.appInstanceId}" }
        val wsSession = WsSession(this, appInstanceId)
        wsSessionManager.registerSession(appInstanceId, wsSession)

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
                            wsMessageHandler.handleMessage(appInstanceId, envelope)
                        }.onFailure { e ->
                            logger.error(e) { "Failed to handle WS message from $appInstanceId" }
                        }
                    }

                    is Frame.Close -> {
                        logger.info { "WebSocket close frame from $appInstanceId" }
                    }

                    else -> {
                        logger.debug { "Ignoring non-text WS frame from $appInstanceId" }
                    }
                }
            }
        } finally {
            logger.info { "WebSocket disconnected: $appInstanceId" }
            wsSessionManager.notifySessionClosed(appInstanceId)
        }
    }
}
