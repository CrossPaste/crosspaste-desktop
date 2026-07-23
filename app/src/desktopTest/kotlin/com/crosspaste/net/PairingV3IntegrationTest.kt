package com.crosspaste.net

import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.pairing.v3.PairingKeySchedule
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PairingTranscript
import com.crosspaste.pairing.v3.PairingTranscriptCodec
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.pairing.v3.PairingV3PinResult
import com.crosspaste.pairing.v3.PairingV3RefreshResult
import com.crosspaste.pairing.v3.PairingV3StartResult
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.pairing.v3.TestPakeProvider
import com.crosspaste.pairing.v3.pairingV3ErrorCodeOf
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PairingV3IntegrationTest {

    private val instances = mutableListOf<TestInstance>()

    private fun createInstance(
        id: String,
        pinLifetime: kotlin.time.Duration = PairingV3.DEFAULT_PIN_LIFETIME,
        generationGrace: kotlin.time.Duration = PairingV3.DEFAULT_GENERATION_GRACE,
    ): TestInstance =
        TestInstance(
            appInstanceId = id,
            pairingPinLifetime = pinLifetime,
            pairingGenerationGrace = generationGrace,
        ).also { instances.add(it) }

    @AfterTest
    fun tearDown() {
        runBlocking {
            instances.forEach { it.stop() }
        }
    }

    private fun urlFor(instance: TestInstance): (io.ktor.http.URLBuilder.() -> Unit) =
        {
            buildUrl(HostAndPort("localhost", instance.getPort()))
        }

    private suspend fun awaitCondition(
        timeout: kotlin.time.Duration = 5.seconds,
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(25L.milliseconds)
        }
        if (!condition()) fail("condition not met within $timeout: $message")
    }

    private fun acceptorCardFor(
        acceptor: TestInstance,
        initiatorAppInstanceId: String,
    ): PairingSessionUiState? =
        acceptor.pairingSessionStore.uiSessionsFlow.value.firstOrNull { card ->
            card.role == PakeRole.ACCEPTOR && card.peerAppInstanceId == initiatorAppInstanceId
        }

    private fun displayedPin(
        acceptor: TestInstance,
        initiatorAppInstanceId: String,
    ): CharArray {
        // A replaced (cancelled) card for the same peer may still be visible; the
        // displayed PIN is the one on the card that currently awaits a proof.
        val card =
            acceptor.pairingSessionStore.uiSessionsFlow.value.firstOrNull { candidate ->
                candidate.role == PakeRole.ACCEPTOR &&
                    candidate.peerAppInstanceId == initiatorAppInstanceId &&
                    candidate.state == PairingSessionState.PIN_AVAILABLE &&
                    candidate.pin != null
            }
        assertNotNull(card, "no PIN-bearing acceptor session card for $initiatorAppInstanceId")
        return card.pin!!.toCharArray()
    }

    private suspend fun startPairing(
        initiator: TestInstance,
        acceptor: TestInstance,
    ): PairingV3StartResult.Started {
        val started =
            initiator.pairingProtocolV3Service.startPairing(
                targetAppInstanceId = acceptor.appInstanceId,
                targetDisplayName = acceptor.appInstanceId,
                toUrl = urlFor(acceptor),
            )
        assertIs<PairingV3StartResult.Started>(started)
        return started
    }

    // ---- Happy path ----

    @Test
    fun testHappyPathPinPairing() =
        runBlocking {
            val a = createInstance("pairing-a")
            val b = createInstance("pairing-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val started = startPairing(b, a)
            val pin = displayedPin(a, b.appInstanceId)

            val result = b.pairingProtocolV3Service.submitPin(started.sessionId, pin, urlFor(a))
            assertIs<PairingV3PinResult.Paired>(result)
            assertEquals(a.appInstanceId, result.peerAppInstanceId)

            // Both sides persisted the correct long-term crypt key
            assertTrue(a.secureIO.existCryptPublicKey(b.appInstanceId))
            assertTrue(b.secureIO.existCryptPublicKey(a.appInstanceId))
            assertContentEquals(
                a.secureIO.serializedPublicKey(b.appInstanceId),
                TestInstance.secureKeyPairSerializer.encodeCryptPublicKey(
                    b.secureKeyPair.cryptKeyPair.publicKey,
                ),
            )
            assertContentEquals(
                b.secureIO.serializedPublicKey(a.appInstanceId),
                TestInstance.secureKeyPairSerializer.encodeCryptPublicKey(
                    a.secureKeyPair.cryptKeyPair.publicKey,
                ),
            )

            // Both session cards are terminal TRUSTED and carry no PIN anymore
            val acceptorCard = acceptorCardFor(a, b.appInstanceId)
            assertNotNull(acceptorCard)
            assertEquals(PairingSessionState.TRUSTED, acceptorCard.state)
            assertEquals(null, acceptorCard.pin)
            val initiatorCard =
                b.pairingSessionStore.uiSessionsFlow.value.firstOrNull { card ->
                    card.sessionId == started.sessionId
                }
            assertNotNull(initiatorCard)
            assertEquals(PairingSessionState.TRUSTED, initiatorCard.state)

            // Encrypted heartbeat works after v3 pairing
            val heartbeat = b.syncClientApi.heartbeat(null, a.appInstanceId, urlFor(a))
            assertTrue(heartbeat is SuccessResult, "heartbeat after v3 pairing should succeed")
        }

    // ---- Gate and validation refusals ----

    @Test
    fun testAcceptanceWindowClosedRefusesIntent() =
        runBlocking {
            val a = createInstance("closed-a")
            val b = createInstance("closed-b")
            a.start()
            b.start()

            val result =
                b.pairingProtocolV3Service.startPairing(a.appInstanceId, a.appInstanceId, urlFor(a))
            assertIs<PairingV3StartResult.Refused>(result)
            assertEquals(PairingV3ErrorCode.PAIRING_DISABLED, result.code)
        }

    @Test
    fun testIntentValidationRefusals() =
        runBlocking {
            val a = createInstance("valid-a")
            val b = createInstance("valid-b")
            a.pairingAcceptanceWindow.open()

            val manual = ManualInitiator(b, a)
            val valid = manual.buildIntent()

            suspend fun refusalOf(intent: PairingIntentV3): PairingV3ErrorCode {
                val result =
                    a.pairingProtocolV3Service.handleIntent(
                        intent,
                        callerAppInstanceId = intent.initiatorAppInstanceId,
                        remoteAddress = null,
                    )
                assertIs<com.crosspaste.pairing.v3.PairingV3ServerResult.Refused>(result)
                return result.code
            }

            assertEquals(
                PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED,
                refusalOf(valid.copy(protocolVersion = 2)),
            )
            assertEquals(
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID,
                refusalOf(valid.copy(targetAppInstanceId = "someone-else")),
            )
            assertEquals(
                PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED,
                refusalOf(valid.copy(supportedCiphersuites = listOf("UNKNOWN-SUITE"))),
            )
            // Tampering any signed field invalidates the intent signature
            assertEquals(
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID,
                refusalOf(valid.copy(initiatorDisplayName = "tampered")),
            )
            assertEquals(
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID,
                refusalOf(valid.copy(initiatorNonce = ByteArray(PairingV3.NONCE_SIZE))),
            )
            // A caller identity that does not match the signed intent is rejected
            val mismatch =
                a.pairingProtocolV3Service.handleIntent(valid, "other-caller", null)
            assertIs<com.crosspaste.pairing.v3.PairingV3ServerResult.Refused>(mismatch)
            assertEquals(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID, mismatch.code)
        }

    @Test
    fun testNetworkRouteIgnoresUnknownV3Fields() =
        runBlocking {
            val a = createInstance("compatible-a")
            val b = createInstance("compatible-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val valid = ManualInitiator(b, a).buildIntent()
            val payload =
                getJsonUtils().JSON.encodeToString(valid).dropLast(1) +
                    ",\"futureOptionalField\":\"tolerated-by-design\"}"
            val response =
                b.pasteClient.postBinary(
                    payload.encodeToByteArray(),
                    contentType = ContentType.Application.Json,
                    urlBuilder = {
                        buildUrl(HostAndPort("localhost", a.getPort()))
                        buildUrl("sync", "pairing", "v3", "intent")
                    },
                )

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, a.pairingSessionStore.activeSessions().size)
        }

    // ---- Wrong PIN, budget, and recovery ----

    @Test
    fun testWrongPinInvalidatesGenerationAndRecovers() =
        runBlocking {
            val a = createInstance("wrong-a")
            val b = createInstance("wrong-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val started = startPairing(b, a)
            val pin = displayedPin(a, b.appInstanceId)
            val wrongPin = pin.copyOf()
            wrongPin[5] = if (wrongPin[5] == '9') '0' else wrongPin[5] + 1

            val failed = b.pairingProtocolV3Service.submitPin(started.sessionId, wrongPin, urlFor(a))
            assertIs<PairingV3PinResult.Refused>(failed)
            assertEquals(PairingV3ErrorCode.PAIRING_PROOF_INVALID, failed.code)

            // The acceptor invalidated the generation immediately and rotates a new PIN
            awaitCondition(message = "acceptor rotates a fresh generation after failed proof") {
                val card = acceptorCardFor(a, b.appInstanceId)
                card != null &&
                    card.state == PairingSessionState.PIN_AVAILABLE &&
                    card.tokenGeneration >= started.tokenGeneration + 1 &&
                    card.pin != null
            }

            // The initiator refreshes the offer, adopts the new generation, and succeeds
            val refreshed = b.pairingProtocolV3Service.refreshOffer(started.sessionId, urlFor(a))
            assertIs<PairingV3RefreshResult.Refreshed>(refreshed)
            assertTrue(refreshed.tokenGeneration >= started.tokenGeneration + 1)

            val freshPin = displayedPin(a, b.appInstanceId)
            val paired = b.pairingProtocolV3Service.submitPin(started.sessionId, freshPin, urlFor(a))
            assertIs<PairingV3PinResult.Paired>(paired)
            assertTrue(a.secureIO.existCryptPublicKey(b.appInstanceId))
        }

    // ---- Concurrency, capacity, and restart ----

    @Test
    fun testConcurrentSessionsIndependent() =
        runBlocking {
            val a = createInstance("multi-a")
            val b = createInstance("multi-b")
            val c = createInstance("multi-c")
            a.start()
            b.start()
            c.start()
            a.pairingAcceptanceWindow.open()

            val startedB = startPairing(b, a)
            val startedC = startPairing(c, a)

            val pinB = displayedPin(a, b.appInstanceId)
            val pinC = displayedPin(a, c.appInstanceId)
            assertNotEquals(pinB.concatToString(), pinC.concatToString())

            // Completing B leaves C untouched
            val pairedB = b.pairingProtocolV3Service.submitPin(startedB.sessionId, pinB, urlFor(a))
            assertIs<PairingV3PinResult.Paired>(pairedB)

            val cardC = acceptorCardFor(a, c.appInstanceId)
            assertNotNull(cardC)
            assertEquals(PairingSessionState.PIN_AVAILABLE, cardC.state)
            assertEquals(pinC.concatToString(), cardC.pin)

            val pairedC = c.pairingProtocolV3Service.submitPin(startedC.sessionId, pinC, urlFor(a))
            assertIs<PairingV3PinResult.Paired>(pairedC)
            assertTrue(a.secureIO.existCryptPublicKey(b.appInstanceId))
            assertTrue(a.secureIO.existCryptPublicKey(c.appInstanceId))
        }

    @Test
    fun testCapacityExceeded() =
        runBlocking {
            val a = createInstance("cap-a")
            a.start()
            a.pairingAcceptanceWindow.open()

            repeat(PairingV3.DEFAULT_MAX_ACTIVE_INCOMING_SESSIONS) { index ->
                val initiator = createInstance("cap-init-$index")
                initiator.start()
                startPairing(initiator, a)
            }

            val overflow = createInstance("cap-overflow")
            overflow.start()
            val refused =
                overflow.pairingProtocolV3Service.startPairing(a.appInstanceId, a.appInstanceId, urlFor(a))
            assertIs<PairingV3StartResult.Refused>(refused)
            assertEquals(PairingV3ErrorCode.PAIRING_CAPACITY_EXCEEDED, refused.code)
        }

    @Test
    fun testRestartReplacesStaleAcceptorSession() =
        runBlocking {
            val a = createInstance("restart-a")
            val b = createInstance("restart-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val first = startPairing(b, a)
            // The initiator lost its state and starts over with a fresh intent
            val second = startPairing(b, a)
            assertNotEquals(first.sessionId, second.sessionId)

            val active =
                a.pairingSessionStore.activeSessions().filter { session ->
                    session.peerAppInstanceId == b.appInstanceId
                }
            assertEquals(1, active.size)
            assertEquals(second.sessionId, active.first().sessionId)

            val pin = displayedPin(a, b.appInstanceId)
            val paired = b.pairingProtocolV3Service.submitPin(second.sessionId, pin, urlFor(a))
            assertTrue(paired is PairingV3PinResult.Paired, "pairing after restart should succeed")
        }

    // ---- Downgrade guard ----

    @Test
    fun testV2ExchangeAndConfirmRemainCompatibleWithoutV3Session() =
        runBlocking {
            val a = createInstance("legacy-a")
            val b = createInstance("legacy-b")
            a.start()
            b.start()

            assertIs<SuccessResult>(b.syncClientApi.exchangeKeys(a.appInstanceId, urlFor(a)))
            assertIs<SuccessResult>(
                b.syncClientApi.trustV2Confirm(a.appInstanceId, "localhost", urlFor(a)),
            )
            assertTrue(a.secureIO.existCryptPublicKey(b.appInstanceId))
        }

    @Test
    fun testDowngradeGuardRejectsV2TrustDuringActiveV3Session() =
        runBlocking {
            val a = createInstance("guard-a")
            val b = createInstance("guard-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            startPairing(b, a)

            val v2Trust = b.syncClientApi.trust(a.appInstanceId, "localhost", a.getToken(), urlFor(a))
            assertIs<FailureResult>(v2Trust)
            assertEquals(
                PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED,
                pairingV3ErrorCodeOf(v2Trust.exception.getErrorCode().code),
            )
        }

    @Test
    fun testDowngradeGuardRejectsV2ExchangeAndConfirmDuringActiveV3Session() =
        runBlocking {
            val a = createInstance("guard2-a")
            val b = createInstance("guard2-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            // The v2 exchange completes BEFORE any v3 session exists
            val exchange = b.syncClientApi.exchangeKeys(a.appInstanceId, urlFor(a))
            assertIs<SuccessResult>(exchange)

            startPairing(b, a)

            // With a v3 session in flight, both remaining v2 legs are refused
            val v2Exchange = b.syncClientApi.exchangeKeys(a.appInstanceId, urlFor(a))
            assertIs<FailureResult>(v2Exchange)
            assertEquals(
                PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED,
                pairingV3ErrorCodeOf(v2Exchange.exception.getErrorCode().code),
            )
            val v2Confirm = b.syncClientApi.trustV2Confirm(a.appInstanceId, "localhost", urlFor(a))
            assertIs<FailureResult>(v2Confirm)
            assertEquals(
                PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED,
                pairingV3ErrorCodeOf(v2Confirm.exception.getErrorCode().code),
            )
            assertTrue(!a.secureIO.existCryptPublicKey(b.appInstanceId))
        }

    @Test
    fun testDowngradeGuardAlsoProtectsLocalInitiatorSession() =
        runBlocking {
            val a = createInstance("guard3-a")
            val b = createInstance("guard3-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            startPairing(b, a)

            val reverseV2Exchange = a.syncClientApi.exchangeKeys(b.appInstanceId, urlFor(b))
            assertIs<FailureResult>(reverseV2Exchange)
            assertEquals(
                PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED,
                pairingV3ErrorCodeOf(reverseV2Exchange.exception.getErrorCode().code),
            )
            assertTrue(!b.secureIO.existCryptPublicKey(a.appInstanceId))
        }

    // ---- Rotation ----

    @Test
    fun testRotationPublishesNewGenerationAndStaleProofFails() =
        runBlocking {
            val a = createInstance("rotate-a", pinLifetime = 300L.milliseconds, generationGrace = 200L.milliseconds)
            val b = createInstance("rotate-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val started = startPairing(b, a)
            val firstGeneration = started.tokenGeneration
            val firstPin = displayedPin(a, b.appInstanceId).concatToString()

            awaitCondition(message = "PIN generation rotates after its lifetime") {
                val card = acceptorCardFor(a, b.appInstanceId)
                card != null && card.tokenGeneration > firstGeneration && card.pin != null
            }
            val rotatedCard = acceptorCardFor(a, b.appInstanceId)
            assertNotNull(rotatedCard)
            assertNotEquals(firstPin, rotatedCard.pin)

            // A proof against the stale generation is refused as expired
            val stale =
                b.pairingProtocolV3Service.submitPin(started.sessionId, firstPin.toCharArray(), urlFor(a))
            assertIs<PairingV3PinResult.Refused>(stale)
            assertEquals(PairingV3ErrorCode.PAIRING_PIN_EXPIRED, stale.code)

            // Refreshing adopts the current generation
            val refreshed = b.pairingProtocolV3Service.refreshOffer(started.sessionId, urlFor(a))
            assertIs<PairingV3RefreshResult.Refreshed>(refreshed)
            assertTrue(refreshed.tokenGeneration > firstGeneration)
        }

    // ---- Cancel ----

    @Test
    fun testCancelPropagatesToAcceptor() =
        runBlocking {
            val a = createInstance("cancel-a")
            val b = createInstance("cancel-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val started = startPairing(b, a)
            assertTrue(b.pairingProtocolV3Service.cancelSession(started.sessionId, urlFor(a)))

            awaitCondition(message = "acceptor session moves to CANCELLED") {
                acceptorCardFor(a, b.appInstanceId)?.state == PairingSessionState.CANCELLED
            }
        }

    // ---- Byte-level flows via a manual initiator ----

    @Test
    fun testCommitRetryReturnsIdenticalReceipt() =
        runBlocking {
            val a = createInstance("commit-a")
            val b = createInstance("commit-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val manual = ManualInitiator(b, a)
            val offer = manual.requestOffer(urlFor(a))
            val pin = displayedPin(a, b.appInstanceId)
            val proof = manual.buildProof(offer, pin)
            val proofResponse = manual.sendProof(proof, urlFor(a))
            manual.verifyProofResponse(offer, proofResponse)

            val commit = manual.buildCommit()
            val firstAck = manual.sendCommit(commit, urlFor(a))
            val secondAck = manual.sendCommit(commit, urlFor(a))
            assertEquals(firstAck, secondAck)
            assertTrue(a.secureIO.existCryptPublicKey(b.appInstanceId))

            // A commit with different bytes for the same session is rejected
            val tamperedCommit =
                commit.copy(commitMac = commit.commitMac.copyOf().also { mac -> mac[0] = mac[0].inc() })
            val conflict = b.pairingV3ClientApi.sendCommit(tamperedCommit, urlFor(a))
            assertPairingFailure(conflict, PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)
        }

    @Test
    fun testPrunedSessionCommitReplayIsBoundToTheCommittingPeer() =
        runBlocking {
            val a = createInstance("prune-a")
            val b = createInstance("prune-b")
            val attacker = createInstance("prune-attacker")
            a.start()
            b.start()
            attacker.start()
            a.pairingAcceptanceWindow.open()

            val manual = ManualInitiator(b, a)
            val offer = manual.requestOffer(urlFor(a))
            val pin = displayedPin(a, b.appInstanceId)
            val proof = manual.buildProof(offer, pin)
            manual.verifyProofResponse(offer, manual.sendProof(proof, urlFor(a)))
            val commit = manual.buildCommit()
            manual.sendCommit(commit, urlFor(a))

            // Simulate the terminal card being pruned while the receipt is still cached
            val sessionIdHex =
                offer.sessionId.joinToString("") { byte ->
                    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                }
            assertTrue(a.pairingSessionStore.removeTerminal(sessionIdHex))

            // A captured commit replayed from a DIFFERENT app instance resolves nothing
            val replayedByAttacker = attacker.pairingV3ClientApi.sendCommit(commit, urlFor(a))
            assertPairingFailure(replayedByAttacker, PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)

            // The rightful peer still gets the original authenticated receipt
            val replayedByPeer = manual.sendCommit(commit, urlFor(a))
            assertContentEquals(commit.transcriptHash, replayedByPeer.transcriptHash)
        }

    @Test
    fun testReplayedIntentAfterTerminalStateIsConsumed() =
        runBlocking {
            val a = createInstance("replay-a")
            val b = createInstance("replay-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val manual = ManualInitiator(b, a)
            val offer = manual.requestOffer(urlFor(a))
            val pin = displayedPin(a, b.appInstanceId)
            val proof = manual.buildProof(offer, pin)
            val proofResponse = manual.sendProof(proof, urlFor(a))
            manual.verifyProofResponse(offer, proofResponse)
            manual.sendCommit(manual.buildCommit(), urlFor(a))

            // Replaying the byte-identical intent after completion is refused
            val replay = b.pairingV3ClientApi.sendIntent(manual.intent, urlFor(a))
            assertPairingFailure(replay, PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)
        }

    @Test
    fun testTamperedProofConfirmationIsRejected() =
        runBlocking {
            val a = createInstance("tamper-a")
            val b = createInstance("tamper-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val manual = ManualInitiator(b, a)
            val offer = manual.requestOffer(urlFor(a))
            val pin = displayedPin(a, b.appInstanceId)
            val proof = manual.buildProof(offer, pin)
            val tampered =
                proof.copy(
                    initiatorKeyConfirmation =
                        proof.initiatorKeyConfirmation.copyOf().also { mac -> mac[0] = mac[0].inc() },
                )

            val refused = b.pairingV3ClientApi.sendProof(tampered, urlFor(a))
            assertPairingFailure(refused, PairingV3ErrorCode.PAIRING_PROOF_INVALID)

            // The failed proof consumed the generation's only attempt: even the
            // correct proof for the same generation is now refused
            val correctRetry = b.pairingV3ClientApi.sendProof(proof, urlFor(a))
            assertPairingFailure(correctRetry, PairingV3ErrorCode.PAIRING_PIN_EXPIRED)
        }

    @Test
    fun testIntentRateLimited() =
        runBlocking {
            val a = createInstance("rate-a")
            val b = createInstance("rate-b")
            a.start()
            b.start()
            a.pairingAcceptanceWindow.open()

            val manual = ManualInitiator(b, a)
            manual.buildIntent()
            var limited = false
            repeat(10) {
                val result = b.pairingV3ClientApi.sendIntent(manual.intent, urlFor(a))
                if (result is FailureResult &&
                    pairingV3ErrorCodeOf(result.exception.getErrorCode().code) ==
                    PairingV3ErrorCode.PAIRING_RATE_LIMITED
                ) {
                    limited = true
                }
            }
            assertTrue(limited, "expected the per-key rate limit to trigger")
        }

    private fun assertPairingFailure(
        result: ClientApiResult,
        expected: PairingV3ErrorCode,
    ) {
        assertIs<FailureResult>(result)
        assertEquals(expected, pairingV3ErrorCodeOf(result.exception.getErrorCode().code))
    }

    /**
     * Drives the initiator side of the protocol by hand — byte-level control for
     * retry, replay, and tamper scenarios that the service API deliberately hides.
     */
    private class ManualInitiator(
        private val initiator: TestInstance,
        private val acceptor: TestInstance,
    ) {
        private val pakeProvider = TestPakeProvider()

        lateinit var intent: PairingIntentV3
        private lateinit var intentHash: ByteArray
        private lateinit var offer: PairingOfferV3
        private lateinit var transcriptHash: ByteArray
        private lateinit var keys: com.crosspaste.pairing.v3.PairingSessionKeys

        suspend fun buildIntent(): PairingIntentV3 {
            val signPublicKey =
                initiator.secureKeyPair.getSignPublicKeyBytes(TestInstance.secureKeyPairSerializer)
            val cryptPublicKey =
                initiator.secureKeyPair.getCryptPublicKeyBytes(TestInstance.secureKeyPairSerializer)
            val unsigned =
                PairingIntentV3(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    requestId = Random.nextBytes(PairingV3.REQUEST_ID_SIZE),
                    initiatorAppInstanceId = initiator.appInstanceId,
                    targetAppInstanceId = acceptor.appInstanceId,
                    initiatorDisplayName = initiator.appInstanceId,
                    initiatorSignPublicKey = signPublicKey,
                    initiatorCryptPublicKey = cryptPublicKey,
                    initiatorNonce = Random.nextBytes(PairingV3.NONCE_SIZE),
                    supportedCiphersuites = listOf(pakeProvider.ciphersuite),
                    signature = ByteArray(0),
                )
            intent =
                unsigned.copy(
                    signature =
                        CryptographyUtils.signData(initiator.secureKeyPair.signKeyPair.privateKey) {
                            PairingTranscriptCodec.encodeIntentSignaturePayload(unsigned)
                        },
                )
            intentHash = PairingTranscriptCodec.intentHash(intent)
            return intent
        }

        suspend fun requestOffer(toUrl: io.ktor.http.URLBuilder.() -> Unit): PairingOfferV3 {
            if (!::intent.isInitialized) {
                buildIntent()
            }
            val sent = initiator.pairingV3ClientApi.sendIntent(intent, toUrl)
            assertIs<SuccessResult>(sent)
            offer = sent.getResult<PairingOfferV3>()
            return offer
        }

        suspend fun buildProof(
            offer: PairingOfferV3,
            pin: CharArray,
        ): PairingProofV3 {
            val signPublicKey =
                initiator.secureKeyPair.getSignPublicKeyBytes(TestInstance.secureKeyPairSerializer)
            val cryptPublicKey =
                initiator.secureKeyPair.getCryptPublicKeyBytes(TestInstance.secureKeyPairSerializer)
            val pinContext =
                PairingTranscriptCodec.encodePinContext(
                    sessionId = offer.sessionId,
                    tokenGeneration = offer.tokenGeneration,
                    acceptorAppInstanceId = acceptor.appInstanceId,
                    initiatorAppInstanceId = initiator.appInstanceId,
                    acceptorSignPublicKey = offer.acceptorSignPublicKey,
                    acceptorCryptPublicKey = offer.acceptorCryptPublicKey,
                    initiatorSignPublicKey = signPublicKey,
                    initiatorCryptPublicKey = cryptPublicKey,
                )
            val pakeSession =
                pakeProvider.createSession(
                    role = PakeRole.INITIATOR,
                    pin = pin,
                    context =
                        com.crosspaste.pairing.v3.PakeContext(
                            sessionId = offer.sessionId,
                            initiatorAppInstanceId = initiator.appInstanceId,
                            acceptorAppInstanceId = acceptor.appInstanceId,
                            pinContext = pinContext,
                        ),
                )
            val localShare = pakeSession.localShare()
            val sharedSecret = pakeSession.deriveSharedSecret(offer.acceptorPakeShare)
            val transcript =
                PairingTranscript(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    selectedCiphersuite = offer.selectedCiphersuite,
                    sessionId = offer.sessionId,
                    tokenGeneration = offer.tokenGeneration,
                    initiatorAppInstanceId = initiator.appInstanceId,
                    acceptorAppInstanceId = acceptor.appInstanceId,
                    initiatorNonce = intent.initiatorNonce,
                    acceptorNonce = offer.acceptorNonce,
                    initiatorSignPublicKey = signPublicKey,
                    initiatorCryptPublicKey = cryptPublicKey,
                    acceptorSignPublicKey = offer.acceptorSignPublicKey,
                    acceptorCryptPublicKey = offer.acceptorCryptPublicKey,
                    initiatorPakeShare = localShare,
                    acceptorPakeShare = offer.acceptorPakeShare,
                    intentHash = intentHash,
                    offerHash = PairingTranscriptCodec.offerHash(offer),
                    negotiatedCapabilities = emptyList(),
                )
            transcriptHash = PairingTranscriptCodec.transcriptHash(transcript)
            keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, sharedSecret)
            return PairingProofV3(
                sessionId = offer.sessionId,
                tokenGeneration = offer.tokenGeneration,
                initiatorPakeShare = localShare,
                transcriptHash = transcriptHash,
                initiatorKeyConfirmation = PairingKeySchedule.initiatorConfirmation(keys, transcriptHash),
                initiatorIdentitySignature =
                    CryptographyUtils.signData(initiator.secureKeyPair.signKeyPair.privateKey) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.INITIATOR, transcriptHash)
                    },
            )
        }

        suspend fun sendProof(
            proof: PairingProofV3,
            toUrl: io.ktor.http.URLBuilder.() -> Unit,
        ): com.crosspaste.dto.pairing.v3.PairingProofResponseV3 {
            val sent = initiator.pairingV3ClientApi.sendProof(proof, toUrl)
            assertIs<SuccessResult>(sent)
            return sent.getResult()
        }

        suspend fun verifyProofResponse(
            offer: PairingOfferV3,
            response: com.crosspaste.dto.pairing.v3.PairingProofResponseV3,
        ) {
            assertContentEquals(transcriptHash, response.transcriptHash)
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.acceptorConfirmation(keys, transcriptHash),
                    response.acceptorKeyConfirmation,
                ),
            )
            val acceptorSignKey =
                TestInstance.secureKeyPairSerializer.decodeSignPublicKey(offer.acceptorSignPublicKey)
            assertTrue(
                CryptographyUtils.verifyData(acceptorSignKey, response.acceptorIdentitySignature) {
                    PairingKeySchedule.identitySignaturePayload(PakeRole.ACCEPTOR, transcriptHash)
                },
            )
        }

        suspend fun buildCommit(): PairingCommitV3 =
            PairingCommitV3(
                sessionId = offer.sessionId,
                transcriptHash = transcriptHash,
                commitMac = PairingKeySchedule.commitMac(keys, transcriptHash),
            )

        suspend fun sendCommit(
            commit: PairingCommitV3,
            toUrl: io.ktor.http.URLBuilder.() -> Unit,
        ): PairingCommitAckV3 {
            val sent = initiator.pairingV3ClientApi.sendCommit(commit, toUrl)
            assertIs<SuccessResult>(sent)
            val ack = sent.getResult<PairingCommitAckV3>()
            assertContentEquals(transcriptHash, ack.transcriptHash)
            assertTrue(
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.receiptMac(keys, transcriptHash),
                    ack.receiptMac,
                ),
            )
            return ack
        }
    }
}
