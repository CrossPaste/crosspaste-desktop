package com.crosspaste.net.ws

import com.crosspaste.app.AppInfo
import com.crosspaste.net.SyncApi
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WsClientConnector(
    private val appInfo: AppInfo,
    private val client: HttpClient,
    private val secureStore: SecureStore,
    private val wsSessionManager: WsSessionManager,
    private val wsMessageHandler: WsMessageHandler,
) {
    private val logger = KotlinLogging.logger {}
    private val json = getJsonUtils().JSON
    private val connectScope = namedScope(ioDispatcher, "WsClientConnector")
    private val inFlightLock = SynchronizedObject()
    private val inFlightTokens = HashMap<String, Any>()

    /**
     * Attempt to open a WebSocket connection to a remote peer.
     * This is called opportunistically after HTTP CONNECTED state is reached.
     * Returns true if the WebSocket was successfully established.
     */
    suspend fun connect(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ): Boolean = connectInternal(host, port, targetAppInstanceId)

    private suspend fun connectInternal(
        host: String,
        port: Int,
        targetAppInstanceId: String,
        onHandshakeFinished: () -> Unit = {},
    ): Boolean =
        runCatching {
            val path =
                "/ws/sync?appInstanceId=${appInfo.appInstanceId}" +
                    "&targetAppInstanceId=$targetAppInstanceId" +
                    "&authVersion=${WsAuthenticationCodec.VERSION}"

            client.webSocket(host = host, port = port, path = path) {
                val rawSession = WsSession(this, targetAppInstanceId)
                val receivedChallenge =
                    withTimeoutOrNull(WS_AUTHENTICATION_TIMEOUT) { receiveWsEnvelope(incoming) }
                        ?: error("WebSocket authentication challenge timed out")
                require(receivedChallenge.envelope.type == WsMessageType.AUTH_CHALLENGE) {
                    "Expected WebSocket authentication challenge"
                }
                val challenge =
                    json.decodeFromString<WsAuthChallenge>(receivedChallenge.envelope.payload.decodeToString())
                val processor = secureStore.getMessageProcessor(targetAppInstanceId)
                val proof =
                    WsAuthProof(
                        processor.authenticationCode(
                            WsAuthenticationCodec.handshakePayload(
                                role = WsAuthenticationCodec.CLIENT_PROOF,
                                sourceAppInstanceId = appInfo.appInstanceId,
                                targetAppInstanceId = targetAppInstanceId,
                                challenge = challenge,
                            ),
                        ),
                        pairingVersion = appInfo.pairingVersion,
                        capabilities = WsCapability.supported,
                    )
                rawSession.sendEnvelope(
                    WsEnvelope(
                        type = WsMessageType.AUTH_PROOF,
                        payload = json.encodeToString(proof).encodeToByteArray(),
                    ),
                )
                val receivedAck =
                    withTimeoutOrNull(WS_AUTHENTICATION_TIMEOUT) { receiveWsEnvelope(incoming) }
                        ?: error("WebSocket authentication acknowledgement timed out")
                require(receivedAck.envelope.type == WsMessageType.AUTH_ACK) {
                    "Expected WebSocket authentication acknowledgement"
                }
                val ack = json.decodeFromString<WsAuthProof>(receivedAck.envelope.payload.decodeToString())
                require(
                    processor.verifyAuthentication(
                        WsAuthenticationCodec.handshakePayload(
                            role = WsAuthenticationCodec.SERVER_PROOF,
                            sourceAppInstanceId = targetAppInstanceId,
                            targetAppInstanceId = appInfo.appInstanceId,
                            challenge = challenge,
                        ),
                        ack.authenticationCode,
                    ),
                ) { "WebSocket server authentication failed" }

                val authenticationContext =
                    WsAuthenticationContext(
                        sessionId = challenge.sessionId,
                        localAppInstanceId = appInfo.appInstanceId,
                        remoteAppInstanceId = targetAppInstanceId,
                        processor = processor,
                    )
                logger.info { "Authenticated WebSocket connected to $targetAppInstanceId at $host:$port" }
                val wsSession =
                    WsSession(
                        this,
                        targetAppInstanceId,
                        authenticationContext,
                        peerSupportsChunkedPayload = SyncApi.supportsPairingV3(challenge.pairingVersion),
                        peerCapabilities = challenge.capabilities,
                    )
                wsSessionManager.registerSession(targetAppInstanceId, wsSession)
                onHandshakeFinished()

                try {
                    while (true) {
                        val received = receiveWsEnvelope(incoming) ?: break
                        if (!authenticationContext.verify(received.header, received.envelope.payload)) {
                            logger.warn { "Rejected unauthenticated WS message from $targetAppInstanceId" }
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid message authentication"))
                            break
                        }
                        wsMessageHandler.handleMessage(targetAppInstanceId, received.envelope)
                    }
                } finally {
                    logger.info { "WebSocket client disconnected from $targetAppInstanceId" }
                    wsSessionManager.notifySessionClosed(targetAppInstanceId, wsSession)
                }
            }
            true
        }.onFailure { e ->
            logger.debug(e) { "WebSocket connection to $targetAppInstanceId at $host:$port failed (fallback to HTTP)" }
        }.getOrDefault(false)

    /**
     * Attempt WebSocket connection in the background.
     * Deduplicates concurrent handshake attempts for the same peer and silently handles failures.
     */
    fun connectAsync(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ) {
        val token = Any()
        val acquired =
            synchronized(inFlightLock) {
                if (targetAppInstanceId in inFlightTokens) {
                    false
                } else {
                    inFlightTokens[targetAppInstanceId] = token
                    true
                }
            }
        if (!acquired) {
            logger.debug { "WebSocket connect already in flight for $targetAppInstanceId, skipping duplicate dial" }
            return
        }
        val release = {
            synchronized(inFlightLock) {
                if (inFlightTokens[targetAppInstanceId] === token) {
                    inFlightTokens.remove(targetAppInstanceId)
                }
            }
        }
        try {
            connectScope.launch {
                try {
                    connectInternal(host, port, targetAppInstanceId, onHandshakeFinished = release)
                } finally {
                    release()
                }
            }
        } catch (e: Throwable) {
            release()
            throw e
        }
    }
}
