package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.net.SyncApi
import com.crosspaste.net.ws.WS_AUTHENTICATION_TIMEOUT
import com.crosspaste.net.ws.WsAuthChallenge
import com.crosspaste.net.ws.WsAuthProof
import com.crosspaste.net.ws.WsAuthenticationCodec
import com.crosspaste.net.ws.WsAuthenticationContext
import com.crosspaste.net.ws.WsCapability
import com.crosspaste.net.ws.WsEnvelope
import com.crosspaste.net.ws.WsMessageHandler
import com.crosspaste.net.ws.WsMessageType
import com.crosspaste.net.ws.WsServerAuthentication
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

fun Routing.wsRouting(
    appInfo: AppInfo,
    secureStore: SecureStore,
    wsSessionManager: WsSessionManager,
    wsMessageHandler: WsMessageHandler,
) {
    val logger = KotlinLogging.logger {}

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

        val authenticationRequested =
            call.request.queryParameters["authVersion"] == WsAuthenticationCodec.VERSION.toString()
        val serverAuthentication =
            if (authenticationRequested) {
                authenticateWebSocketPeer(appInfo, appInstanceId, secureStore) ?: return@webSocket
            } else {
                val remoteAddress = runCatching { call.request.origin.remoteAddress }.getOrNull()
                if (!isLoopbackAddress(remoteAddress)) {
                    // Deliberately loud: a peer landing here dials without authVersion
                    // (e.g. an outdated extension) and would otherwise disappear without
                    // a trace — the peer's own onopen already fired, so it believes it
                    // is connected while we never register the session.
                    logger.warn {
                        "WS connection rejected: $appInstanceId from $remoteAddress " +
                            "requires authenticated WebSocket (authVersion missing)"
                    }
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authenticated WebSocket required"))
                    return@webSocket
                }
                logger.warn { "Allowing legacy unauthenticated WebSocket from loopback for $appInstanceId" }
                null
            }
        val authenticationContext = serverAuthentication?.context

        logger.info { "WebSocket connected: $appInstanceId → ${appInfo.appInstanceId}" }
        val wsSession =
            WsSession(
                this,
                appInstanceId,
                authenticationContext,
                peerSupportsChunkedPayload =
                    SyncApi.supportsPairingV3(serverAuthentication?.remotePairingVersion),
                peerCapabilities = serverAuthentication?.remoteCapabilities.orEmpty(),
            )
        wsSessionManager.registerSession(appInstanceId, wsSession)

        try {
            receiveWebSocketMessages(appInstanceId, authenticationContext, wsMessageHandler)
        } finally {
            logger.info { "WebSocket disconnected: $appInstanceId" }
            wsSessionManager.notifySessionClosed(appInstanceId, wsSession)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.authenticateWebSocketPeer(
    appInfo: AppInfo,
    appInstanceId: String,
    secureStore: SecureStore,
): WsServerAuthentication? {
    val json = getJsonUtils().JSON
    val processor = runCatching { secureStore.getMessageProcessor(appInstanceId) }.getOrNull()
    if (processor == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "WebSocket authentication unavailable"))
        return null
    }
    val rawSession = WsSession(this, appInstanceId)
    val challenge =
        WsAuthChallenge(
            sessionId = getCodecsUtils().base64Encode(CryptographyRandom.nextBytes(16)),
            nonce = CryptographyRandom.nextBytes(32),
            pairingVersion = appInfo.pairingVersion,
            capabilities = WsCapability.supported,
        )
    rawSession.sendEnvelope(
        WsEnvelope(
            type = WsMessageType.AUTH_CHALLENGE,
            payload = json.encodeToString(challenge).encodeToByteArray(),
        ),
    )
    val proof =
        runCatching {
            withTimeoutOrNull(WS_AUTHENTICATION_TIMEOUT) { receiveWsEnvelope(incoming) }
                ?.takeIf { it.envelope.type == WsMessageType.AUTH_PROOF }
                ?.let { json.decodeFromString<WsAuthProof>(it.envelope.payload.decodeToString()) }
        }.getOrNull()
    val proofPayload =
        WsAuthenticationCodec.handshakePayload(
            role = WsAuthenticationCodec.CLIENT_PROOF,
            sourceAppInstanceId = appInstanceId,
            targetAppInstanceId = appInfo.appInstanceId,
            challenge = challenge,
        )
    if (proof == null || !processor.verifyAuthentication(proofPayload, proof.authenticationCode)) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "WebSocket authentication failed"))
        return null
    }

    val ackPayload =
        WsAuthenticationCodec.handshakePayload(
            role = WsAuthenticationCodec.SERVER_PROOF,
            sourceAppInstanceId = appInfo.appInstanceId,
            targetAppInstanceId = appInstanceId,
            challenge = challenge,
        )
    val ack = WsAuthProof(processor.authenticationCode(ackPayload))
    rawSession.sendEnvelope(
        WsEnvelope(
            type = WsMessageType.AUTH_ACK,
            payload = json.encodeToString(ack).encodeToByteArray(),
        ),
    )
    return WsServerAuthentication(
        context =
            WsAuthenticationContext(
                sessionId = challenge.sessionId,
                localAppInstanceId = appInfo.appInstanceId,
                remoteAppInstanceId = appInstanceId,
                processor = processor,
            ),
        remotePairingVersion = proof.pairingVersion,
        remoteCapabilities = proof.capabilities,
    )
}

private suspend fun DefaultWebSocketServerSession.receiveWebSocketMessages(
    appInstanceId: String,
    authenticationContext: WsAuthenticationContext?,
    wsMessageHandler: WsMessageHandler,
) {
    val logger = KotlinLogging.logger {}
    while (true) {
        val receivedResult = runCatching { receiveWsEnvelope(incoming) }
        val receiveFailure = receivedResult.exceptionOrNull()
        if (receiveFailure != null) {
            if (receiveFailure is CancellationException) throw receiveFailure
            if (authenticationContext != null) {
                logger.warn { "Rejected malformed authenticated WS message from $appInstanceId" }
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Malformed authenticated message"))
                break
            }
            logger.error(receiveFailure) { "Failed to handle legacy WS message from $appInstanceId" }
            continue
        }
        val received = receivedResult.getOrNull() ?: break
        if (authenticationContext != null &&
            !authenticationContext.verify(received.header, received.envelope.payload)
        ) {
            logger.warn { "Rejected unauthenticated WS message from $appInstanceId" }
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid message authentication"))
            break
        }
        wsMessageHandler.handleMessage(appInstanceId, received.envelope)
    }
}

internal fun isLoopbackAddress(address: String?): Boolean =
    address == "127.0.0.1" || address == "::1" || address == "0:0:0:0:0:0:0:1"
