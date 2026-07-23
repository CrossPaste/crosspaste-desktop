package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of the real [Spake2PakeProvider] over the BouncyCastle
 * backend: the same PIN + context yields agreement, and any divergence (wrong PIN,
 * different context, swapped roles, tampered share) fails key agreement — exactly
 * the contract the surrounding pairing protocol relies on.
 */
class Spake2PakeProviderTest {

    private val provider = Spake2PakeProvider(BouncyCastlePakeEcOps())

    private val sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x0A }

    private fun context(
        tokenGeneration: Long = 1L,
        sessionIdBytes: ByteArray = sessionId,
    ): PakeContext {
        val pinContext =
            PairingTranscriptCodec.encodePinContext(
                sessionId = sessionIdBytes,
                tokenGeneration = tokenGeneration,
                acceptorAppInstanceId = "acceptor-app",
                initiatorAppInstanceId = "initiator-app",
                acceptorSignPublicKey = ByteArray(8) { 0x66 },
                acceptorCryptPublicKey = ByteArray(8) { 0x77 },
                initiatorSignPublicKey = ByteArray(8) { 0x44 },
                initiatorCryptPublicKey = ByteArray(8) { 0x55 },
            )
        return PakeContext(
            sessionId = sessionIdBytes,
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

            // Uncompressed P-256 points: 65 bytes, 0x04 prefix.
            assertEquals(65, initiatorShare.size)
            assertEquals(0x04.toByte(), initiatorShare[0])

            val initiatorKe = initiator.deriveSharedSecret(acceptorShare)
            val acceptorKe = acceptor.deriveSharedSecret(initiatorShare)
            assertEquals(Spake2P256.KE_LENGTH, initiatorKe.size)
            assertContentEquals(initiatorKe, acceptorKe)
        }

    @Test
    fun wrongPinDisagrees() =
        runTest {
            val ctx = context()
            val initiator = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            val acceptor = provider.createSession(PakeRole.ACCEPTOR, "000000".toCharArray(), ctx)

            val initiatorKe = initiator.deriveSharedSecret(acceptor.localShare())
            val acceptorKe = acceptor.deriveSharedSecret(initiator.localShare())
            assertFalse(initiatorKe.contentEquals(acceptorKe))
        }

    @Test
    fun differentContextDisagreesEvenWithSamePin() =
        runTest {
            val initiator =
                provider.createSession(
                    PakeRole.INITIATOR,
                    "620632".toCharArray(),
                    context(tokenGeneration = 1L),
                )
            val acceptor =
                provider.createSession(
                    PakeRole.ACCEPTOR,
                    "620632".toCharArray(),
                    context(tokenGeneration = 2L),
                )

            val initiatorKe = initiator.deriveSharedSecret(acceptor.localShare())
            val acceptorKe = acceptor.deriveSharedSecret(initiator.localShare())
            assertFalse(initiatorKe.contentEquals(acceptorKe))
        }

    @Test
    fun sameRoleOnBothSidesDoesNotConverge() =
        runTest {
            val ctx = context()
            // Both believe they are the initiator: a role mismatch (M vs N) must not agree.
            val one = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            val two = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)

            val keOne = one.deriveSharedSecret(two.localShare())
            val keTwo = two.deriveSharedSecret(one.localShare())
            assertFalse(keOne.contentEquals(keTwo))
        }

    @Test
    fun freshSessionsUseFreshRandomness() =
        runTest {
            val ctx = context()
            val shareA = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx).localShare()
            val shareB = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx).localShare()
            // Same role, same PIN, same context, but independent x: shares differ.
            assertFalse(shareA.contentEquals(shareB))
        }

    @Test
    fun malformedPeerShareIsRejected() =
        runTest {
            val ctx = context()
            val initiator = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            assertFailsWith<PakeException> {
                initiator.deriveSharedSecret(ByteArray(65) { 0x04 })
            }
        }

    @Test
    fun destroyedSessionRefusesUse() =
        runTest {
            val ctx = context()
            val session = provider.createSession(PakeRole.INITIATOR, "620632".toCharArray(), ctx)
            session.destroy()
            assertFailsWith<IllegalStateException> { session.localShare() }
        }

    /**
     * The real provider must drop into the full v3 handshake: its Ke, fed through
     * the frozen HKDF key schedule and transcript-bound MACs, produces matching
     * mutual confirmation and commit MACs on both sides. This mirrors the
     * FakePakeProvider flow test with the production SPAKE2 provider.
     */
    @Test
    fun realProviderDrivesFullMutualConfirmation() =
        runTest {
            val ctx = context()
            val pin = "620632"
            val initiator = provider.createSession(PakeRole.INITIATOR, pin.toCharArray(), ctx)
            val acceptor = provider.createSession(PakeRole.ACCEPTOR, pin.toCharArray(), ctx)

            val initiatorShare = initiator.localShare()
            val acceptorShare = acceptor.localShare()
            val initiatorKe = initiator.deriveSharedSecret(acceptorShare)
            val acceptorKe = acceptor.deriveSharedSecret(initiatorShare)

            val transcriptHash =
                PairingTranscriptCodec.transcriptHash(
                    PairingTranscript(
                        protocolVersion = PairingV3.PROTOCOL_VERSION,
                        selectedCiphersuite = PairingV3.CIPHERSUITE_SPAKE2_P256,
                        sessionId = sessionId,
                        tokenGeneration = 1L,
                        initiatorAppInstanceId = "initiator-app",
                        acceptorAppInstanceId = "acceptor-app",
                        initiatorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x22 },
                        acceptorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x33 },
                        initiatorSignPublicKey = ByteArray(8) { 0x44 },
                        initiatorCryptPublicKey = ByteArray(8) { 0x55 },
                        acceptorSignPublicKey = ByteArray(8) { 0x66 },
                        acceptorCryptPublicKey = ByteArray(8) { 0x77 },
                        initiatorPakeShare = initiatorShare,
                        acceptorPakeShare = acceptorShare,
                        intentHash = ByteArray(32) { 0xAA.toByte() },
                        offerHash = ByteArray(32) { 0xBB.toByte() },
                        negotiatedCapabilities = emptyList(),
                    ),
                )

            val initiatorKeys = PairingKeySchedule.deriveSessionKeys(transcriptHash, initiatorKe)
            val acceptorKeys = PairingKeySchedule.deriveSessionKeys(transcriptHash, acceptorKe)

            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.initiatorConfirmation(acceptorKeys, transcriptHash),
                    PairingKeySchedule.initiatorConfirmation(initiatorKeys, transcriptHash),
                ),
            )
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.acceptorConfirmation(initiatorKeys, transcriptHash),
                    PairingKeySchedule.acceptorConfirmation(acceptorKeys, transcriptHash),
                ),
            )
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.commitMac(acceptorKeys, transcriptHash),
                    PairingKeySchedule.commitMac(initiatorKeys, transcriptHash),
                ),
            )
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.receiptMac(initiatorKeys, transcriptHash),
                    PairingKeySchedule.receiptMac(acceptorKeys, transcriptHash),
                ),
            )
        }
}
