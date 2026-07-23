package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the Phase 1 building blocks end to end with the deterministic fake PAKE:
 * PIN derivation → PAKE → transcript → key schedule → mutual confirmation → commit MACs.
 * Network, session store, and real SPAKE2 are covered in later phases.
 */
class PairingV3FlowTest {

    private val sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x0A }

    private val pakeContext =
        PakeContext(
            sessionId = sessionId,
            initiatorAppInstanceId = "initiator-app",
            acceptorAppInstanceId = "acceptor-app",
        )

    private suspend fun acceptorPin(): CharArray {
        val pinSecret = ByteArray(PairingV3.PIN_SECRET_SIZE) { 0x5A }
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
        return PairingPinGenerator.derivePin(pinSecret, pinContext)
    }

    private fun transcript(
        initiatorShare: ByteArray,
        acceptorShare: ByteArray,
    ): PairingTranscript =
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
        )

    @Test
    fun testHappyPathMutualConfirmation() =
        runTest {
            val provider = FakePakeProvider()
            val displayedPin = acceptorPin()

            // Acceptor displays the PIN; the initiator's user enters the same digits
            val enteredPin = displayedPin.copyOf()

            val acceptorSession = provider.createSession(PakeRole.ACCEPTOR, displayedPin, pakeContext)
            val initiatorSession = provider.createSession(PakeRole.INITIATOR, enteredPin, pakeContext)

            val acceptorShare = acceptorSession.localShare()
            val initiatorShare = initiatorSession.localShare()

            val initiatorSecret = initiatorSession.deriveSharedSecret(acceptorShare)
            val acceptorSecret = acceptorSession.deriveSharedSecret(initiatorShare)
            assertContentEquals(initiatorSecret, acceptorSecret)

            // Both roles construct the same transcript independently
            val initiatorHash = PairingTranscriptCodec.transcriptHash(transcript(initiatorShare, acceptorShare))
            val acceptorHash = PairingTranscriptCodec.transcriptHash(transcript(initiatorShare, acceptorShare))
            assertContentEquals(initiatorHash, acceptorHash)

            val initiatorKeys = PairingKeySchedule.deriveSessionKeys(initiatorHash, initiatorSecret)
            val acceptorKeys = PairingKeySchedule.deriveSessionKeys(acceptorHash, acceptorSecret)

            // Initiator proves, acceptor verifies
            val initiatorConfirmation = PairingKeySchedule.initiatorConfirmation(initiatorKeys, initiatorHash)
            val expectedByAcceptor = PairingKeySchedule.initiatorConfirmation(acceptorKeys, acceptorHash)
            assertTrue(PairingKeySchedule.constantTimeEquals(expectedByAcceptor, initiatorConfirmation))

            // Acceptor proves, initiator verifies
            val acceptorConfirmation = PairingKeySchedule.acceptorConfirmation(acceptorKeys, acceptorHash)
            val expectedByInitiator = PairingKeySchedule.acceptorConfirmation(initiatorKeys, initiatorHash)
            assertTrue(PairingKeySchedule.constantTimeEquals(expectedByInitiator, acceptorConfirmation))

            // Commit and receipt round-trip
            val commitMac = PairingKeySchedule.commitMac(initiatorKeys, initiatorHash)
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.commitMac(acceptorKeys, acceptorHash),
                    commitMac,
                ),
            )
            val receiptMac = PairingKeySchedule.receiptMac(acceptorKeys, acceptorHash)
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.receiptMac(initiatorKeys, initiatorHash),
                    receiptMac,
                ),
            )
        }

    @Test
    fun testWrongPinFailsConfirmation() =
        runTest {
            val provider = FakePakeProvider()
            val displayedPin = acceptorPin()

            // Off-by-one digit
            val wrongPin = displayedPin.copyOf()
            wrongPin[5] = if (wrongPin[5] == '9') '0' else wrongPin[5] + 1

            val acceptorSession = provider.createSession(PakeRole.ACCEPTOR, displayedPin, pakeContext)
            val initiatorSession = provider.createSession(PakeRole.INITIATOR, wrongPin, pakeContext)

            val acceptorShare = acceptorSession.localShare()
            val initiatorShare = initiatorSession.localShare()

            val initiatorSecret = initiatorSession.deriveSharedSecret(acceptorShare)
            val acceptorSecret = acceptorSession.deriveSharedSecret(initiatorShare)
            assertFalse(initiatorSecret.contentEquals(acceptorSecret))

            val transcriptHash = PairingTranscriptCodec.transcriptHash(transcript(initiatorShare, acceptorShare))
            val initiatorKeys = PairingKeySchedule.deriveSessionKeys(transcriptHash, initiatorSecret)
            val acceptorKeys = PairingKeySchedule.deriveSessionKeys(transcriptHash, acceptorSecret)

            val initiatorConfirmation = PairingKeySchedule.initiatorConfirmation(initiatorKeys, transcriptHash)
            val expectedByAcceptor = PairingKeySchedule.initiatorConfirmation(acceptorKeys, transcriptHash)
            assertFalse(PairingKeySchedule.constantTimeEquals(expectedByAcceptor, initiatorConfirmation))
        }

    @Test
    fun testTranscriptMismatchFailsConfirmationEvenWithCorrectPin() =
        runTest {
            val provider = FakePakeProvider()
            val displayedPin = acceptorPin()

            val acceptorSession = provider.createSession(PakeRole.ACCEPTOR, displayedPin, pakeContext)
            val initiatorSession = provider.createSession(PakeRole.INITIATOR, displayedPin.copyOf(), pakeContext)

            val acceptorShare = acceptorSession.localShare()
            val initiatorShare = initiatorSession.localShare()

            val initiatorSecret = initiatorSession.deriveSharedSecret(acceptorShare)
            val acceptorSecret = acceptorSession.deriveSharedSecret(initiatorShare)

            // A MITM substituted the initiator sign key in the acceptor's view
            val initiatorView = transcript(initiatorShare, acceptorShare)
            val acceptorView = initiatorView.copy(initiatorSignPublicKey = ByteArray(8) { 0x45 })

            val initiatorKeys =
                PairingKeySchedule.deriveSessionKeys(
                    PairingTranscriptCodec.transcriptHash(initiatorView),
                    initiatorSecret,
                )
            val acceptorKeys =
                PairingKeySchedule.deriveSessionKeys(
                    PairingTranscriptCodec.transcriptHash(acceptorView),
                    acceptorSecret,
                )

            val initiatorConfirmation =
                PairingKeySchedule.initiatorConfirmation(
                    initiatorKeys,
                    PairingTranscriptCodec.transcriptHash(initiatorView),
                )
            val expectedByAcceptor =
                PairingKeySchedule.initiatorConfirmation(
                    acceptorKeys,
                    PairingTranscriptCodec.transcriptHash(acceptorView),
                )
            assertFalse(PairingKeySchedule.constantTimeEquals(expectedByAcceptor, initiatorConfirmation))
        }

    @Test
    fun testInconsistentRoleAssignmentFailsSecretAgreement() =
        runTest {
            val provider = FakePakeProvider()
            val displayedPin = acceptorPin()

            // Both endpoints mistakenly believe they are the INITIATOR: with a real
            // SPAKE2 this is a use of the wrong RFC 9382 constant (M vs N) and must
            // not converge; the fake reproduces that via fixed role ordering
            val sessionOne = provider.createSession(PakeRole.INITIATOR, displayedPin, pakeContext)
            val sessionTwo = provider.createSession(PakeRole.INITIATOR, displayedPin.copyOf(), pakeContext)

            val shareOne = sessionOne.localShare()
            val shareTwo = sessionTwo.localShare()

            val secretOne = sessionOne.deriveSharedSecret(shareTwo)
            val secretTwo = sessionTwo.deriveSharedSecret(shareOne)

            assertFalse(secretOne.contentEquals(secretTwo))
        }

    @Test
    fun testCrossSessionProofIsRejected() =
        runTest {
            val provider = FakePakeProvider()
            val displayedPin = acceptorPin()

            // Same PIN digits reused in a different session must not validate
            val otherContext =
                PakeContext(
                    sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x0B },
                    initiatorAppInstanceId = "initiator-app",
                    acceptorAppInstanceId = "acceptor-app",
                )

            val acceptorSession = provider.createSession(PakeRole.ACCEPTOR, displayedPin, pakeContext)
            val initiatorSession = provider.createSession(PakeRole.INITIATOR, displayedPin.copyOf(), otherContext)

            val acceptorShare = acceptorSession.localShare()
            val initiatorShare = initiatorSession.localShare()

            val initiatorSecret = initiatorSession.deriveSharedSecret(acceptorShare)
            val acceptorSecret = acceptorSession.deriveSharedSecret(initiatorShare)

            assertFalse(initiatorSecret.contentEquals(acceptorSecret))
        }
}
