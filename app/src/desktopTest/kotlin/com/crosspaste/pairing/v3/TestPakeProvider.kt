package com.crosspaste.pairing.v3

/**
 * Deterministic PAKE stand-in for app-level protocol tests, NOT a real PAKE.
 *
 * Mirrors `core/src/commonTest/.../FakePakeProvider.kt` (test source sets cannot
 * be shared across modules): both roles derive the same shared secret if and only
 * if they used the same PIN, the same canonical context, each other's real
 * shares, and a consistent INITIATOR/ACCEPTOR role assignment. It provides none
 * of SPAKE2's security.
 */
class TestPakeProvider : PakeProvider {

    override val ciphersuite: String = "FAKE-PAKE-FOR-TESTS"

    private var instanceCounter = 0

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession = TestPakeSession(role, pin.concatToString(), context, instanceCounter++)
}

private class TestPakeSession(
    private val role: PakeRole,
    private val pin: String,
    private val context: PakeContext,
    private val instanceId: Int,
) : PakeSession {

    private var destroyed = false

    override suspend fun localShare(): ByteArray {
        check(!destroyed) { "session destroyed" }
        // instanceId stands in for the per-session ephemeral scalar of a real PAKE
        val seed = "fake-share-${role.name}-$instanceId"
        val x =
            PairingKeySchedule.hmacSha256(
                key = pin.encodeToByteArray() + context.pinContext,
                data = "$seed-x".encodeToByteArray(),
            )
        val y =
            PairingKeySchedule.hmacSha256(
                key = pin.encodeToByteArray() + context.pinContext,
                data = "$seed-y".encodeToByteArray(),
            )
        return byteArrayOf(PairingV3.PAKE_SHARE_UNCOMPRESSED_PREFIX) + x + y
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
