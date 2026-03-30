package com.crosspaste.net.ws

import kotlinx.serialization.Serializable

/**
 * Wire header sent as a Text frame (JSON).
 * When [hasPayload] is true, the next frame MUST be a Binary frame containing the payload bytes.
 */
@Serializable
data class WsEnvelopeHeader(
    val type: String,
    val encrypted: Boolean = false,
    val hasPayload: Boolean = false,
    val requestId: String? = null,
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
    const val ERROR = "error"
}
