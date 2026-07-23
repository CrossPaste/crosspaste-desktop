package com.crosspaste.pairing.v3

/**
 * Deterministic PAKE stand-in for protocol tests, NOT a real PAKE.
 *
 * It mimics the contract that matters to the surrounding protocol: both roles derive
 * the same shared secret if and only if they used the same PIN, the same session
 * context, each other's real shares, AND a consistent INITIATOR/ACCEPTOR role
 * assignment. Shares enter the derivation in fixed role order (initiator first) —
 * never sorted — so a role-mapping mistake produces mismatched secrets here just
 * like it would with RFC 9382 constants M and N. It provides none of SPAKE2's
 * security.
 */
class FakePakeProvider : PakeProvider {

    override val ciphersuite: String = "FAKE-PAKE-FOR-TESTS"

    private var instanceCounter = 0

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession = FakePakeSession(role, pin.concatToString(), context, instanceCounter++)
}

private class FakePakeSession(
    private val role: PakeRole,
    private val pin: String,
    private val context: PakeContext,
    private val instanceId: Int,
) : PakeSession {

    private var destroyed = false

    override suspend fun localShare(): ByteArray {
        check(!destroyed) { "session destroyed" }
        // instanceId stands in for the per-session ephemeral scalar of a real PAKE:
        // two sessions never emit the same share even for the same role and PIN
        return PairingKeySchedule.hmacSha256(
            key = pin.encodeToByteArray() + context.pinContext,
            data = "fake-share-${role.name}-$instanceId".encodeToByteArray(),
        )
    }

    override suspend fun deriveSharedSecret(peerShare: ByteArray): ByteArray {
        check(!destroyed) { "session destroyed" }
        if (peerShare.isEmpty()) {
            throw PakeException("empty peer share")
        }
        val localShare = localShare()
        // Fixed role order, mirroring RFC 9382's asymmetric A/B transcript
        val (initiatorShare, acceptorShare) =
            when (role) {
                PakeRole.INITIATOR -> localShare to peerShare
                PakeRole.ACCEPTOR -> peerShare to localShare
            }
        return PairingKeySchedule.hmacSha256(
            key = pin.encodeToByteArray() + context.pinContext,
            data =
                context.initiatorAppInstanceId.encodeToByteArray() +
                    context.acceptorAppInstanceId.encodeToByteArray() +
                    initiatorShare + acceptorShare,
        )
    }

    override fun destroy() {
        destroyed = true
    }
}
