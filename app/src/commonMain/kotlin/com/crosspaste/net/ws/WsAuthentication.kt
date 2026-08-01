package com.crosspaste.net.ws

import com.crosspaste.pairing.v3.CanonicalWriter
import com.crosspaste.secure.SecureMessageProcessor
import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

internal val WS_AUTHENTICATION_TIMEOUT = 5.seconds

@Serializable
data class WsAuthChallenge(
    val sessionId: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val nonce: ByteArray,
)

@Serializable
data class WsAuthProof(
    @Serializable(with = Base64ByteArraySerializer::class)
    val authenticationCode: ByteArray,
)

class WsAuthenticationContext(
    private val sessionId: String,
    private val localAppInstanceId: String,
    private val remoteAppInstanceId: String,
    private val processor: SecureMessageProcessor,
) {
    private var nextSendSequence = 0L
    private var nextReceiveSequence = 0L

    suspend fun createHeader(envelope: WsEnvelope): WsEnvelopeHeader {
        check(nextSendSequence != Long.MAX_VALUE) { "WebSocket authentication sequence exhausted" }
        val sequence = nextSendSequence++
        val authenticationCode =
            processor.authenticationCode(
                WsAuthenticationCodec.envelopePayload(
                    sessionId = sessionId,
                    sequence = sequence,
                    sourceAppInstanceId = localAppInstanceId,
                    targetAppInstanceId = remoteAppInstanceId,
                    envelope = envelope,
                ),
            )
        return envelope.toHeader().copy(
            authSessionId = sessionId,
            authSequence = sequence,
            authenticationCode = authenticationCode,
        )
    }

    suspend fun verify(
        header: WsEnvelopeHeader,
        payload: ByteArray,
    ): Boolean {
        val sequence = header.authSequence ?: return false
        val authenticationCode = header.authenticationCode ?: return false
        if (header.authSessionId != sessionId || sequence != nextReceiveSequence) return false
        val envelope =
            WsEnvelope(
                type = header.type,
                payload = payload,
                encrypted = header.encrypted,
                requestId = header.requestId,
            )
        val valid =
            processor.verifyAuthentication(
                WsAuthenticationCodec.envelopePayload(
                    sessionId = sessionId,
                    sequence = sequence,
                    sourceAppInstanceId = remoteAppInstanceId,
                    targetAppInstanceId = localAppInstanceId,
                    envelope = envelope,
                ),
                authenticationCode,
            )
        if (valid) nextReceiveSequence++
        return valid
    }
}

object WsAuthenticationCodec {
    const val VERSION = 1
    const val CLIENT_PROOF = "client-proof"
    const val SERVER_PROOF = "server-proof"

    fun handshakePayload(
        role: String,
        sourceAppInstanceId: String,
        targetAppInstanceId: String,
        challenge: WsAuthChallenge,
    ): ByteArray =
        CanonicalWriter("crosspaste-ws-handshake-v1")
            .field(1, role)
            .field(2, sourceAppInstanceId)
            .field(3, targetAppInstanceId)
            .field(4, challenge.sessionId)
            .field(5, challenge.nonce)
            .build()

    fun envelopePayload(
        sessionId: String,
        sequence: Long,
        sourceAppInstanceId: String,
        targetAppInstanceId: String,
        envelope: WsEnvelope,
    ): ByteArray =
        CanonicalWriter("crosspaste-ws-envelope-v1")
            .field(1, sessionId)
            .field(2, sequence)
            .field(3, sourceAppInstanceId)
            .field(4, targetAppInstanceId)
            .field(5, envelope.type)
            .field(6, if (envelope.encrypted) 1 else 0)
            .field(7, envelope.requestId ?: "")
            .field(8, envelope.payload)
            .build()
}
