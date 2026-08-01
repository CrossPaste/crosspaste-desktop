package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.net.ws.WsAuthChallenge
import com.crosspaste.net.ws.WsAuthProof
import com.crosspaste.net.ws.WsAuthenticationCodec
import com.crosspaste.net.ws.WsAuthenticationContext
import com.crosspaste.net.ws.WsEnvelope
import com.crosspaste.net.ws.WsMessageHandler
import com.crosspaste.net.ws.WsMessageType
import com.crosspaste.net.ws.WsSession
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.net.ws.receiveWsEnvelope
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getJsonUtils
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeoutOrNull

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

        val rawSession = WsSession(this, appInstanceId)
        val authenticationContext =
            if (call.request.queryParameters["authVersion"] == WsAuthenticationCodec.VERSION.toString()) {
                val processor = secureStore.getMessageProcessor(appInstanceId)
                val challenge =
                    WsAuthChallenge(
                        sessionId = getCodecsUtils().base64Encode(CryptographyRandom.nextBytes(16)),
                        nonce = CryptographyRandom.nextBytes(32),
                    )
                rawSession.sendEnvelope(
                    WsEnvelope(
                        type = WsMessageType.AUTH_CHALLENGE,
                        payload = json.encodeToString(challenge).encodeToByteArray(),
                    ),
                )
                val receivedProof = withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) { receiveWsEnvelope(incoming) }
                val proof =
                    receivedProof
                        ?.takeIf { it.envelope.type == WsMessageType.AUTH_PROOF }
                        ?.let { json.decodeFromString<WsAuthProof>(it.envelope.payload.decodeToString()) }
                val validProof =
                    proof != null &&
                        processor.verifyAuthentication(
                            WsAuthenticationCodec.handshakePayload(
                                role = WsAuthenticationCodec.CLIENT_PROOF,
                                sourceAppInstanceId = appInstanceId,
                                targetAppInstanceId = appInfo.appInstanceId,
                                challenge = challenge,
                            ),
                            proof.authenticationCode,
                        )
                if (!validProof) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "WebSocket authentication failed"))
                    return@webSocket
                }
                val ack =
                    WsAuthProof(
                        processor.authenticationCode(
                            WsAuthenticationCodec.handshakePayload(
                                role = WsAuthenticationCodec.SERVER_PROOF,
                                sourceAppInstanceId = appInfo.appInstanceId,
                                targetAppInstanceId = appInstanceId,
                                challenge = challenge,
                            ),
                        ),
                    )
                rawSession.sendEnvelope(
                    WsEnvelope(
                        type = WsMessageType.AUTH_ACK,
                        payload = json.encodeToString(ack).encodeToByteArray(),
                    ),
                )
                WsAuthenticationContext(
                    sessionId = challenge.sessionId,
                    localAppInstanceId = appInfo.appInstanceId,
                    remoteAppInstanceId = appInstanceId,
                    processor = processor,
                )
            } else {
                val remoteHost = runCatching { call.request.origin.remoteHost }.getOrNull()
                if (!isLoopbackHost(remoteHost)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authenticated WebSocket required"))
                    return@webSocket
                }
                logger.warn { "Allowing legacy unauthenticated WebSocket from loopback for $appInstanceId" }
                null
            }

        logger.info { "WebSocket connected: $appInstanceId → ${appInfo.appInstanceId}" }
        val wsSession = WsSession(this, appInstanceId, authenticationContext)
        wsSessionManager.registerSession(appInstanceId, wsSession)

        try {
            while (true) {
                val received = receiveWsEnvelope(incoming) ?: break
                if (authenticationContext != null &&
                    !authenticationContext.verify(received.header, received.envelope.payload)
                ) {
                    logger.warn { "Rejected unauthenticated WS message from $appInstanceId" }
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid message authentication"))
                    break
                }
                wsMessageHandler.handleMessage(appInstanceId, received.envelope)
            }
        } finally {
            logger.info { "WebSocket disconnected: $appInstanceId" }
            wsSessionManager.notifySessionClosed(appInstanceId, wsSession)
        }
    }
}

internal fun isLoopbackHost(host: String?): Boolean = host == "127.0.0.1" || host == "::1" || host == "0:0:0:0:0:0:0:1"

private const val AUTHENTICATION_TIMEOUT_MS = 5_000L
