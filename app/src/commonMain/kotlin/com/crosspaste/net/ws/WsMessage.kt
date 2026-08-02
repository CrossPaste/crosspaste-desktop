package com.crosspaste.net.ws

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Upper bound for a single WebSocket frame. Native pairing v2 intentionally
 * keeps its 1 MiB WebSocket payload contract even when a particular client
 * implementation can receive a larger frame; native file bytes use the HTTPS
 * pull/push path and are not constrained by this WebSocket limit. Pairing v3
 * raises the logical-message limit through negotiated chunking rather than a
 * larger frame. A frame over the receiver's limit does not just drop that
 * message — Ktor tears down the whole connection (1009 TOO_BIG) while the
 * sender's local write still succeeds.
 */
const val WS_MAX_FRAME_SIZE: Long = 1024L * 1024

/**
 * Size of each Binary frame when a payload is sent chunked. Matches
 * [WS_MAX_FRAME_SIZE] exactly — Ktor's receive check is strictly
 * greater-than, the same boundary FilePullService's 1 MiB chunks already
 * rely on. Keeping frames small also lets ping/pong interleave between
 * chunks, so heartbeats are not starved during a large transfer on a slow
 * link.
 */
const val WS_PAYLOAD_CHUNK_SIZE: Int = 1024 * 1024

/**
 * Upper bound for a complete (reassembled) payload. Sized as 2x the largest
 * configurable non-file paste (maxNonFilePasteSize, capped at 64 MiB in
 * settings) to cover typical JSON string escaping plus encryption overhead;
 * pathological escaping (control-character-heavy content inflates up to 6x)
 * can still exceed it, in which case the sender guard fails the push cleanly.
 */
const val WS_MAX_PAYLOAD_SIZE: Long = 128L * 1024 * 1024

/** Maximum accepted [WsEnvelopeHeader.payloadChunkCount], derived from the two limits above. */
const val WS_MAX_PAYLOAD_CHUNK_COUNT: Int = (WS_MAX_PAYLOAD_SIZE / WS_PAYLOAD_CHUNK_SIZE).toInt()

/**
 * Wire header sent as a Text frame (JSON).
 * When [hasPayload] is true, the next [payloadChunkCount] frames MUST be Binary
 * frames whose concatenation is the payload bytes.
 *
 * [payloadChunkCount] > 1 may only be sent to a peer that advertised pairing
 * version >= 3 during the WebSocket authentication handshake; legacy peers
 * ignore the field (ignoreUnknownKeys) and always receive a single Binary
 * frame. The count is not covered by the per-message authentication code, but
 * tampering with it only desynchronizes reassembly, which the payload MAC then
 * rejects — it cannot alter accepted data.
 */
@Serializable
data class WsEnvelopeHeader(
    val type: String,
    val encrypted: Boolean = false,
    val hasPayload: Boolean = false,
    val requestId: String? = null,
    val authSessionId: String? = null,
    val authSequence: Long? = null,
    @Serializable(with = Base64ByteArraySerializer::class)
    val authenticationCode: ByteArray? = null,
    val payloadChunkCount: Int = 1,
)

/**
 * In-memory envelope combining the header with the raw payload bytes.
 * Never serialized directly — the wire format is [WsEnvelopeHeader] + optional Binary frame.
 */
data class WsEnvelope(
    val type: String,
    val payload: ByteArray = byteArrayOf(),
    val encrypted: Boolean = false,
    val requestId: String? = null,
) {
    fun toHeader(): WsEnvelopeHeader =
        WsEnvelopeHeader(
            type = type,
            encrypted = encrypted,
            hasPayload = payload.isNotEmpty(),
            requestId = requestId,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WsEnvelope) return false
        return type == other.type &&
            payload.contentEquals(other.payload) &&
            encrypted == other.encrypted &&
            requestId == other.requestId
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + (requestId?.hashCode() ?: 0)
        return result
    }
}

object WsMessageType {
    const val HEARTBEAT = "heartbeat"
    const val HEARTBEAT_ACK = "heartbeat_ack"
    const val PASTE_PUSH = "paste_push"
    const val SYNC_INFO = "sync_info"
    const val NOTIFY_EXIT = "notify_exit"
    const val NOTIFY_REMOVE = "notify_remove"
    const val FILE_PULL_REQUEST = "file_pull_request"
    const val FILE_PULL_RESPONSE = "file_pull_response"
    const val PASTE_REJECTED_OVERSIZE = "paste_rejected_oversize"
    const val ERROR = "error"
    const val AUTH_CHALLENGE = "auth_challenge"
    const val AUTH_PROOF = "auth_proof"
    const val AUTH_ACK = "auth_ack"
}
