package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * End-to-end behaviour of [Spake2PakeProvider] over the noble backend: same PIN
 * and context agree on Ke, wrong PIN diverges, malformed shares throw. Mirrors
 * the invariants of the JVM provider test so both shipping backends carry the
 * same contract.
 */
class NobleSpake2PakeProviderTest {

    private val provider = Spake2PakeProvider(NoblePakeEcOps())

    private val sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x0A }

    private fun context(): PakeContext {
        val pinContext =
            PairingTranscriptCodec.encodePinContext(
                sessionId = sessionId,
                tokenGeneration = 1L,
                acceptorAppInstanceId = "acceptor-app",
                initiatorAppInstanceId = "initiator-app",
                acceptorSignPublicKey = ByteArray(8) { 0x66 },
                acceptorCryptPublicKey = ByteArray(8) { 0x77 },
                initiatorSignPublicKey = ByteArray(8) { 0x44 },
                initiatorCryptPublicKey = ByteArray(8) { 0x55 },
            )
        return PakeContext(
            sessionId = sessionId,
            initiatorAppInstanceId = "initiator-app",
            acceptorAppInstanceId = "acceptor-app",
            pinContext = pinContext,
        )
    }

    @Test
    fun samePinAndContextAgreeOnKe() =
        runTest {
            val ctx = context()
            val initiator = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            val acceptor = provider.createSession(PakeRole.ACCEPTOR, "620632".toCharArray(), ctx)

            val initiatorShare = initiator.localShare()
            val acceptorShare = acceptor.localShare()

            val initiatorKe = initiator.deriveSharedSecret(acceptorShare)
            val acceptorKe = acceptor.deriveSharedSecret(initiatorShare)
            assertContentEquals(initiatorKe, acceptorKe)
        }

    @Test
    fun wrongPinFailsAgreement() =
        runTest {
            val ctx = context()
            val initiator = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            val acceptor = provider.createSession(PakeRole.ACCEPTOR, "620633".toCharArray(), ctx)

            val initiatorKe = initiator.deriveSharedSecret(acceptor.localShare())
            val acceptorKe = acceptor.deriveSharedSecret(initiator.localShare())
            assertFalse(initiatorKe.contentEquals(acceptorKe))
        }

    @Test
    fun swappedRolesFailAgreement() =
        runTest {
            val ctx = context()
            val first = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            val second = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)

            val firstKe = first.deriveSharedSecret(second.localShare())
            val secondKe = second.deriveSharedSecret(first.localShare())
            assertFalse(firstKe.contentEquals(secondKe))
        }

    @Test
    fun malformedPeerShareThrows() =
        runTest {
            val ctx = context()
            val initiator = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            // Wrong length
            assertFailsWith<PakeException> {
                initiator.deriveSharedSecret(ByteArray(64) { 0x01 })
            }
        }

    @Test
    fun offCurvePeerShareThrows() =
        runTest {
            val ctx = context()
            val initiator = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            val bogus = ByteArray(PairingV3.PAKE_SHARE_SIZE) { 0x02 }
            bogus[0] = PairingV3.PAKE_SHARE_UNCOMPRESSED_PREFIX
            assertFailsWith<PakeException> {
                initiator.deriveSharedSecret(bogus)
            }
        }
}
