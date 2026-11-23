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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WsClientConnector(
    private val appInfo: AppInfo,
    private val client: HttpClient,
    private val secureStore: SecureStore,
    private val wsSessionManager: WsSessionManager,
    private val wsMessageHandler: WsMessageHandler,
) {
    private class InFlightHandshake(
        val host: String,
        val port: Int,
        val job: Job,
    )

    private val logger = KotlinLogging.logger {}
    private val json = getJsonUtils().JSON
    private val connectScope = namedScope(ioDispatcher, "WsClientConnector")
    private val inFlightLock = SynchronizedObject()
    private val inFlightHandshakes = HashMap<String, InFlightHandshake>()

    init {
        wsSessionManager.addOnPausedListener {
            cancelInFlightHandshakes()
        }
    }

    fun cancelInFlightHandshakes() {
        val jobsToCancel =
            synchronized(inFlightLock) {
                if (inFlightHandshakes.isEmpty()) {
                    emptyList()
                } else {
                    val jobs = inFlightHandshakes.values.map { it.job }
                    inFlightHandshakes.clear()
                    jobs
                }
            }
        jobsToCancel.forEach { it.cancel() }
    }

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
            if (!wsSessionManager.isAcceptingSessions()) {
                logger.debug { "WebSocket connect skipped for $targetAppInstanceId at $host:$port (paused)" }
                return false
            }
            val path =
                "/ws/sync?appInstanceId=${appInfo.appInstanceId}" +
                    "&targetAppInstanceId=$targetAppInstanceId" +
                    "&authVersion=${WsAuthenticationCodec.VERSION}"

            var sessionRegistered = false
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
                val registered = wsSessionManager.registerSession(targetAppInstanceId, wsSession)
                onHandshakeFinished()
                if (!registered) {
                    return@webSocket
                }
                sessionRegistered = true

                dispatchWsSessionMessages(
                    appInstanceId = targetAppInstanceId,
                    wsSession = wsSession,
                    wsMessageHandler = wsMessageHandler,
                    onReadLoopFinished = {
                        logger.info { "WebSocket client disconnected from $targetAppInstanceId" }
                        wsSessionManager.notifySessionClosed(targetAppInstanceId, wsSession)
                    },
                ) { dispatch ->
                    while (true) {
                        val received =
                            receiveWsEnvelope(incoming, onFrameReceived = wsSession::recordInboundActivity)
                                ?: break
                        if (!authenticationContext.verify(received.header, received.envelope.payload)) {
                            logger.warn { "Rejected unauthenticated WS message from $targetAppInstanceId" }
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid message authentication"))
                            break
                        }
                        dispatch(received.envelope)
                    }
                }
            }
            sessionRegistered
        }.onFailure { e ->
            logger.debug(e) { "WebSocket connection to $targetAppInstanceId at $host:$port failed (fallback to HTTP)" }
        }.getOrDefault(false)

    /**
     * Attempt WebSocket connection in the background.
     * Coalesces concurrent handshake attempts to the same peer and endpoint, replaces
     * an in-flight handshake immediately when the peer's host/port changes, and silently
     * handles failures.
     */
    fun connectAsync(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ) {
        var entry: InFlightHandshake? = null
        val release = {
            synchronized(inFlightLock) {
                val current = entry
                if (current != null && inFlightHandshakes[targetAppInstanceId] === current) {
                    inFlightHandshakes.remove(targetAppInstanceId)
                }
            }
        }
        val job =
            connectScope.launch(start = CoroutineStart.LAZY) {
                try {
                    connectInternal(host, port, targetAppInstanceId, onHandshakeFinished = release)
                } finally {
                    release()
                }
            }
        val candidate = InFlightHandshake(host, port, job)
        entry = candidate

        var previousJobToCancel: Job? = null
        val shouldStart =
            synchronized(inFlightLock) {
                if (!wsSessionManager.isAcceptingSessions()) {
                    false
                } else {
                    val existing = inFlightHandshakes[targetAppInstanceId]
                    if (existing != null && existing.host == host && existing.port == port) {
                        false
                    } else {
                        previousJobToCancel = existing?.job
                        inFlightHandshakes[targetAppInstanceId] = candidate
                        true
                    }
                }
            }

        if (!shouldStart) {
            job.cancel()
            logger.debug {
                "WebSocket connect already in flight for $targetAppInstanceId at $host:$port, skipping duplicate dial"
            }
            return
        }

        previousJobToCancel?.let { oldJob ->
            logger.info {
                "Replacing in-flight WebSocket handshake for $targetAppInstanceId with new endpoint $host:$port"
            }
            oldJob.cancel()
        }
        job.start()
    }
}
