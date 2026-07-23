package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Advisory cancellation for one exact pairing session.
 *
 * Before PAKE completion cancellation is advisory by design (doc §10.6): the
 * request only affects the session identified by [sessionId] and only when the
 * caller's app instance id matches that session's peer. Cancelling a session
 * that is already terminal is idempotent. An authenticated (MAC-bound) cancel
 * for post-PAKE states would require a new frozen key-schedule label and is an
 * explicit open protocol decision — not silently invented here.
 */
@Serializable
data class PairingCancelV3(
    @Serializable(with = Base64ByteArraySerializer::class)
    val sessionId: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingCancelV3) return false

        return sessionId.contentEquals(other.sessionId)
    }

    override fun hashCode(): Int = sessionId.contentHashCode()
}
