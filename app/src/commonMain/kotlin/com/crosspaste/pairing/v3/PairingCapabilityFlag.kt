package com.crosspaste.pairing.v3

/**
 * Immutable pairing capability snapshot for one application process.
 *
 * All capability consumers must share the same instance so discovery,
 * credential selection, server routing, and provider registration cannot
 * disagree while the process is running. Remote rollout changes should take
 * effect after restart rather than changing an active pairing session.
 */
data class PairingCapabilityFlag(
    val advertisedPairingVersion: Int,
) {

    init {
        require(advertisedPairingVersion > 0) {
            "advertised pairing version must be positive"
        }
    }

    val isPairingV3Enabled: Boolean =
        advertisedPairingVersion >= PairingV3.PROTOCOL_VERSION
}
