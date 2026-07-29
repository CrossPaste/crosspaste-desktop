package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.utils.CryptographyUtils
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [PairingV3InitiatorEngine] against a scripted in-test acceptor built
 * from the same frozen primitives (codec, key schedule, PIN generator) with the
 * deterministic fake PAKE. Real ECDSA P-256 keys sign both sides, so intent and
 * offer signature verification runs for real on every target (JVM, JS, native).
 */
class PairingV3InitiatorEngineTest {

    private val serializer = SecureKeyPairSerializer()

    private suspend fun newEngine(
        provider: PakeProvider,
        keyPair: SecureKeyPair,
    ): PairingV3InitiatorEngine =
        PairingV3InitiatorEngine(
            appInstanceId = INITIATOR_ID,
            secureKeyPair = keyPair,
            pakeProvider = provider,
            secureKeyPairSerializer = serializer,
        )

    /**
     * Minimal scripted acceptor: verifies what the real server verifies and
     * produces byte-exact protocol messages, without session store or timers.
     */
    private inner class ScriptedAcceptor(
        private val provider: PakeProvider,
        val keyPair: SecureKeyPair,
    ) {
        val sessionId: ByteArray = CryptographyRandom.nextBytes(PairingV3.SESSION_ID_SIZE)
        val nonce: ByteArray = CryptographyRandom.nextBytes(PairingV3.NONCE_SIZE)
        val tokenGeneration = 1L

        private lateinit var intent: PairingIntentV3
        private lateinit var pakeSession: PakeSession
        private lateinit var localShare: ByteArray
        private lateinit var pinContext: ByteArray
        private var offer: PairingOfferV3? = null
        private var keys: PairingSessionKeys? = null
        private var transcriptHash: ByteArray? = null

        lateinit var displayedPin: CharArray
            private set

        suspend fun handleIntent(received: PairingIntentV3): PairingOfferV3 {
            val initiatorSignKey = serializer.decodeSignPublicKey(received.initiatorSignPublicKey)
            assertTrue(
                CryptographyUtils.verifyData(initiatorSignKey, received.signature) {
                    PairingTranscriptCodec.encodeIntentSignaturePayload(received)
                },
                "intent signature must verify",
            )
            intent = received
            val ownSignPublicKey = keyPair.getSignPublicKeyBytes(serializer)
            val ownCryptPublicKey = keyPair.getCryptPublicKeyBytes(serializer)
            pinContext =
                PairingTranscriptCodec.encodePinContext(
                    sessionId = sessionId,
                    tokenGeneration = tokenGeneration,
                    acceptorAppInstanceId = ACCEPTOR_ID,
                    initiatorAppInstanceId = received.initiatorAppInstanceId,
                    acceptorSignPublicKey = ownSignPublicKey,
                    acceptorCryptPublicKey = ownCryptPublicKey,
                    initiatorSignPublicKey = received.initiatorSignPublicKey,
                    initiatorCryptPublicKey = received.initiatorCryptPublicKey,
                )
            displayedPin =
                PairingPinGenerator.derivePin(
                    CryptographyRandom.nextBytes(PairingV3.PIN_SECRET_SIZE),
                    pinContext,
                )
            pakeSession =
                provider.createSession(
                    role = PakeRole.ACCEPTOR,
                    pin = displayedPin,
                    context =
                        PakeContext(
                            sessionId = sessionId,
                            initiatorAppInstanceId = received.initiatorAppInstanceId,
                            acceptorAppInstanceId = ACCEPTOR_ID,
                            pinContext = pinContext,
                        ),
                )
            localShare = pakeSession.localShare()
            val unsigned =
                PairingOfferV3(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    selectedCiphersuite = received.supportedCiphersuites.first(),
                    sessionId = sessionId,
                    requestHash = PairingTranscriptCodec.intentHash(received),
                    tokenGeneration = tokenGeneration,
                    pinExpiresAt = 1_700_000_000_000L,
                    initiatorAppInstanceId = received.initiatorAppInstanceId,
                    acceptorAppInstanceId = ACCEPTOR_ID,
                    acceptorSignPublicKey = ownSignPublicKey,
                    acceptorCryptPublicKey = ownCryptPublicKey,
                    acceptorNonce = nonce,
                    acceptorPakeShare = localShare,
                    signature = ByteArray(0),
                )
            val signed =
                unsigned.copy(
                    signature =
                        CryptographyUtils.signData(keyPair.signKeyPair.privateKey) {
                            PairingTranscriptCodec.encodeOfferSignaturePayload(unsigned)
                        },
                )
            offer = signed
            return signed
        }

        suspend fun handleProof(proof: PairingProofV3): PairingProofResponseV3 {
            val currentOffer = checkNotNull(offer)
            val sharedSecret = pakeSession.deriveSharedSecret(proof.initiatorPakeShare)
            val transcript =
                PairingTranscript(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    selectedCiphersuite = currentOffer.selectedCiphersuite,
                    sessionId = sessionId,
                    tokenGeneration = tokenGeneration,
                    initiatorAppInstanceId = intent.initiatorAppInstanceId,
                    acceptorAppInstanceId = ACCEPTOR_ID,
                    initiatorNonce = intent.initiatorNonce,
                    acceptorNonce = nonce,
                    initiatorSignPublicKey = intent.initiatorSignPublicKey,
                    initiatorCryptPublicKey = intent.initiatorCryptPublicKey,
                    acceptorSignPublicKey = currentOffer.acceptorSignPublicKey,
                    acceptorCryptPublicKey = currentOffer.acceptorCryptPublicKey,
                    initiatorPakeShare = proof.initiatorPakeShare,
                    acceptorPakeShare = localShare,
                    intentHash = PairingTranscriptCodec.intentHash(intent),
                    offerHash = PairingTranscriptCodec.offerHash(currentOffer),
                    negotiatedCapabilities = emptyList(),
                )
            val hash = PairingTranscriptCodec.transcriptHash(transcript)
            assertContentEquals(hash, proof.transcriptHash, "transcript hashes must agree")
            val derivedKeys = PairingKeySchedule.deriveSessionKeys(hash, sharedSecret)
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.initiatorConfirmation(derivedKeys, hash),
                    proof.initiatorKeyConfirmation,
                ),
                "initiator key confirmation must verify",
            )
            val initiatorSignKey = serializer.decodeSignPublicKey(intent.initiatorSignPublicKey)
            assertTrue(
                CryptographyUtils.verifyData(initiatorSignKey, proof.initiatorIdentitySignature) {
                    PairingKeySchedule.identitySignaturePayload(PakeRole.INITIATOR, hash)
                },
                "initiator identity signature must verify",
            )
            keys = derivedKeys
            transcriptHash = hash
            return PairingProofResponseV3(
                sessionId = sessionId,
                transcriptHash = hash,
                acceptorKeyConfirmation = PairingKeySchedule.acceptorConfirmation(derivedKeys, hash),
                acceptorIdentitySignature =
                    CryptographyUtils.signData(keyPair.signKeyPair.privateKey) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.ACCEPTOR, hash)
                    },
            )
        }

        /** Returns the shared-secret confirmation check the real server performs on proof. */
        suspend fun proofConfirmationMatches(proof: PairingProofV3): Boolean {
            val sharedSecret = pakeSession.deriveSharedSecret(proof.initiatorPakeShare)
            val derivedKeys = PairingKeySchedule.deriveSessionKeys(proof.transcriptHash, sharedSecret)
            return PairingKeySchedule.constantTimeEquals(
                PairingKeySchedule.initiatorConfirmation(derivedKeys, proof.transcriptHash),
                proof.initiatorKeyConfirmation,
            )
        }

        suspend fun handleCommit(commit: PairingCommitV3): PairingCommitAckV3 {
            val derivedKeys = checkNotNull(keys)
            val hash = checkNotNull(transcriptHash)
            assertContentEquals(hash, commit.transcriptHash)
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.commitMac(derivedKeys, hash),
                    commit.commitMac,
                ),
                "commit MAC must verify",
            )
            return PairingCommitAckV3(
                sessionId = sessionId,
                transcriptHash = hash,
                receiptMac = PairingKeySchedule.receiptMac(derivedKeys, hash),
            )
        }
    }

    @Test
    fun testHappyPathEndToEnd() =
        runTest {
            val provider = FakePakeProvider()
            val initiatorKeys = CryptographyUtils.generateSecureKeyPair()
            val acceptorKeys = CryptographyUtils.generateSecureKeyPair()
            val engine = newEngine(provider, initiatorKeys)
            val acceptor = ScriptedAcceptor(provider, acceptorKeys)

            val intent = engine.createIntent(ACCEPTOR_ID, "Chrome Extension")
            assertEquals(PairingV3.PROTOCOL_VERSION, intent.protocolVersion)
            assertEquals(INITIATOR_ID, intent.initiatorAppInstanceId)

            val offer = acceptor.handleIntent(intent)
            assertNull(engine.acceptOffer(offer))
            assertEquals(1L, engine.tokenGeneration)
            assertContentEquals(acceptor.sessionId, engine.sessionId)

            val proof = engine.buildProof(acceptor.displayedPin.copyOf())
            val proofResponse = acceptor.handleProof(proof)
            assertTrue(engine.verifyProofResponse(proofResponse))

            val commit = engine.buildCommit()
            val ack = acceptor.handleCommit(commit)
            assertTrue(engine.verifyCommitAck(ack))

            assertContentEquals(
                acceptorKeys.getCryptPublicKeyBytes(serializer),
                engine.acceptorCryptPublicKey,
            )
            assertContentEquals(
                acceptorKeys.getSignPublicKeyBytes(serializer),
                engine.acceptorSignPublicKey,
            )
        }

    @Test
    fun testWrongPinFailsAcceptorSideConfirmation() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            assertNull(engine.acceptOffer(offer))

            val wrongPin = acceptor.displayedPin.copyOf()
            wrongPin[5] = if (wrongPin[5] == '9') '0' else wrongPin[5] + 1
            val proof = engine.buildProof(wrongPin)
            assertFalse(acceptor.proofConfirmationMatches(proof))
        }

    @Test
    fun testOfferWithTamperedSignatureIsRefused() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            val tampered = offer.copy(signature = offer.signature.copyOf().also { it[8]++ })
            assertEquals(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID, engine.acceptOffer(tampered))
        }

    @Test
    fun testOfferWithWrongRequestHashIsRefused() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            val tampered = offer.copy(requestHash = ByteArray(32) { 0x7F })
            assertEquals(PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH, engine.acceptOffer(tampered))
        }

    @Test
    fun testOfferWithUnsupportedCiphersuiteIsRefused() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            val tampered = offer.copy(selectedCiphersuite = "NOT-A-REAL-SUITE")
            assertEquals(
                PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED,
                engine.acceptOffer(tampered),
            )
        }

    @Test
    fun testTamperedProofResponseFailsHard() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            assertNull(engine.acceptOffer(offer))
            val proof = engine.buildProof(acceptor.displayedPin.copyOf())
            val response = acceptor.handleProof(proof)
            val tampered =
                response.copy(
                    acceptorKeyConfirmation = response.acceptorKeyConfirmation.copyOf().also { it[0]++ },
                )
            assertFalse(engine.verifyProofResponse(tampered))
            // The engine is now failed: committing must be impossible.
            assertFailsWith<IllegalStateException> { engine.buildCommit() }
        }

    @Test
    fun testTamperedCommitAckIsRejected() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            assertNull(engine.acceptOffer(offer))
            val proof = engine.buildProof(acceptor.displayedPin.copyOf())
            assertTrue(engine.verifyProofResponse(acceptor.handleProof(proof)))
            val ack = acceptor.handleCommit(engine.buildCommit())
            val tampered = ack.copy(receiptMac = ack.receiptMac.copyOf().also { it[0]++ })
            assertFalse(engine.verifyCommitAck(tampered))
        }

    @Test
    fun testRotatedOfferMustKeepSessionId() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptorKeys = CryptographyUtils.generateSecureKeyPair()
            val acceptor = ScriptedAcceptor(provider, acceptorKeys)
            val otherAcceptor = ScriptedAcceptor(provider, acceptorKeys)

            val intent = engine.createIntent(ACCEPTOR_ID, "Chrome Extension")
            assertNull(engine.acceptOffer(acceptor.handleIntent(intent)))
            // A "rotated" offer from a different session id must be refused.
            val foreignOffer = otherAcceptor.handleIntent(intent)
            assertEquals(
                PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH,
                engine.acceptOffer(foreignOffer),
            )
        }

    @Test
    fun testInvalidPinShapeIsRejectedLocally() =
        runTest {
            val provider = FakePakeProvider()
            val engine = newEngine(provider, CryptographyUtils.generateSecureKeyPair())
            val acceptor = ScriptedAcceptor(provider, CryptographyUtils.generateSecureKeyPair())

            val offer = acceptor.handleIntent(engine.createIntent(ACCEPTOR_ID, "Chrome Extension"))
            assertNull(engine.acceptOffer(offer))
            assertFailsWith<IllegalArgumentException> { engine.buildProof("12345".toCharArray()) }
            assertFailsWith<IllegalArgumentException> { engine.buildProof("12345a".toCharArray()) }
        }

    companion object {
        private const val INITIATOR_ID = "initiator-app"
        private const val ACCEPTOR_ID = "acceptor-app"
    }
}
