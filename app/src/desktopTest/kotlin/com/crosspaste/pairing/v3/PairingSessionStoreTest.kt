package com.crosspaste.pairing.v3

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingSessionStoreTest {

    private var now = 1_000_000L

    private fun newStore(
        maxActiveIncoming: Int = PairingV3.DEFAULT_MAX_ACTIVE_INCOMING_SESSIONS,
        maxIntentTombstones: Int = PairingV3.DEFAULT_MAX_INTENT_TOMBSTONES,
        intentTombstoneTtlMillis: Long = PairingV3.DEFAULT_INTENT_TOMBSTONE_TTL.inWholeMilliseconds,
    ) = PairingSessionStore(
        maxActiveIncoming = maxActiveIncoming,
        maxIntentTombstones = maxIntentTombstones,
        intentTombstoneTtlMillis = intentTombstoneTtlMillis,
        nowEpochMillis = { now },
    )

    private class TrackingPakeSession : PakeSession {
        var destroyed = false

        override suspend fun localShare(): ByteArray = ByteArray(4)

        override suspend fun deriveSharedSecret(peerShare: ByteArray): ByteArray = ByteArray(32)

        override fun destroy() {
            destroyed = true
        }
    }

    private fun evidenceKeys() =
        PairingSessionKeys(
            confirmInitiator = ByteArray(32) { 1 },
            confirmAcceptor = ByteArray(32) { 2 },
            handshakeAead = ByteArray(32) { 3 },
            receipt = ByteArray(32) { 4 },
        )

    private fun session(
        sessionId: String,
        fingerprint: String = "fp-$sessionId",
        requestId: String = "req-$sessionId",
        appInstanceId: String = "app-$fingerprint",
        intentHash: ByteArray = (requestId.encodeToByteArray() + ByteArray(32)).copyOf(32),
        offerHash: ByteArray? = ByteArray(32) { 3 },
        localPakeShare: ByteArray? = ByteArray(8) { 4 },
        pakeSession: PakeSession? = TrackingPakeSession(),
        role: PakeRole = PakeRole.ACCEPTOR,
        state: PairingSessionState = PairingSessionState.PIN_AVAILABLE,
        proofAttempts: Int = 0,
        expiresAt: Long = now + 600_000L,
    ) = PairingSession(
        sessionId = sessionId,
        sessionIdBytes = sessionId.encodeToByteArray(),
        role = role,
        requestId = requestId,
        peerAppInstanceId = appInstanceId,
        peerDisplayName = "Device $sessionId",
        peerSignPublicKey = ByteArray(8) { 1 },
        peerCryptPublicKey = ByteArray(8) { 2 },
        peerKeyFingerprint = fingerprint,
        localNonce = ByteArray(PairingV3.NONCE_SIZE),
        peerNonce = ByteArray(PairingV3.NONCE_SIZE),
        selectedCiphersuite = PairingV3.CIPHERSUITE_SPAKE2_P256,
        negotiatedCapabilities = emptyList(),
        intentHash = intentHash,
        offerHash = offerHash,
        localPakeShare = localPakeShare,
        peerPakeShare = null,
        tokenGeneration = 1L,
        pin = "123456".toCharArray(),
        pinExpiresAt = now + 30_000L,
        generationFrozenUntil = 0L,
        proofAttempts = proofAttempts,
        pakeSession = pakeSession,
        sessionKeys = null,
        transcriptHash = null,
        state = state,
        createdAt = now,
        expiresAt = expiresAt,
    )

    private suspend fun PairingSessionStore.confirmPeer(sessionId: String) {
        val proof =
            completeProof(
                sessionId = sessionId,
                generation = 1L,
                transcriptHash = ByteArray(32) { 9 },
                sessionKeys = evidenceKeys(),
                peerPakeShare = ByteArray(8) { 5 },
            )
        assertIs<PairingCompleteProofResult.Confirmed>(proof)
    }

    // region create: dedup, identity conflict, capacity, collision

    @Test
    fun testIndependentSessionsCoexist() =
        runTest {
            val store = newStore()

            assertIs<PairingSessionCreateResult.Created>(store.create(session("b")))
            assertIs<PairingSessionCreateResult.Created>(store.create(session("c")))

            assertEquals(2, store.activeSessions().size)
            assertEquals(2, store.uiSessionsFlow.value.size)
        }

    @Test
    fun testSessionIdIsNeverOverwritten() =
        runTest {
            val store = newStore()
            store.create(session("b", fingerprint = "fp-x"))

            // Same session id from a different peer, and even from an initiator-role
            // session, must be rejected — never silently replace the stored session
            assertIs<PairingSessionCreateResult.SessionIdCollision>(
                store.create(session("b", fingerprint = "fp-y", appInstanceId = "app-other")),
            )
            assertIs<PairingSessionCreateResult.SessionIdCollision>(
                store.create(session("b", role = PakeRole.INITIATOR)),
            )

            assertEquals("fp-x", store.get("b")?.peerKeyFingerprint)
        }

    @Test
    fun testByteIdenticalIntentRetryIsIdempotent() =
        runTest {
            val store = newStore()
            val intentHash = ByteArray(32) { 7 }
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash))

            val result =
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash))

            val duplicate = assertIs<PairingSessionCreateResult.Duplicate>(result)
            assertEquals("b", duplicate.existing.sessionId)
            assertEquals(1, store.activeSessions().size)
        }

    @Test
    fun testSameRequestIdWithDifferentIntentBytesIsNotDuplicate() =
        runTest {
            val store = newStore()
            store.create(
                session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = ByteArray(32) { 7 }),
            )

            val result =
                store.create(
                    session("b2", fingerprint = "fp-x", requestId = "req-1", intentHash = ByteArray(32) { 8 }),
                )

            assertIs<PairingSessionCreateResult.PeerAlreadyActive>(result)
        }

    @Test
    fun testSameFingerprintDifferentRequestIsRejected() =
        runTest {
            val store = newStore()
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1"))

            assertIs<PairingSessionCreateResult.PeerAlreadyActive>(
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-2")),
            )
        }

    @Test
    fun testSameAppInstanceIdWithDifferentSigningKeyIsIdentityConflict() =
        runTest {
            val store = newStore()
            store.create(session("b", fingerprint = "fp-x", appInstanceId = "app-1"))

            assertIs<PairingSessionCreateResult.IdentityConflict>(
                store.create(session("c", fingerprint = "fp-y", appInstanceId = "app-1")),
            )
        }

    @Test
    fun testCapacityExceeded() =
        runTest {
            val store = newStore(maxActiveIncoming = 4)
            repeat(4) { index -> assertIs<PairingSessionCreateResult.Created>(store.create(session("s$index"))) }

            assertIs<PairingSessionCreateResult.CapacityExceeded>(store.create(session("s4")))
        }

    @Test
    fun testTerminalSessionFreesCapacityAndFingerprint() =
        runTest {
            val store = newStore(maxActiveIncoming = 1)
            store.create(session("b", fingerprint = "fp-x"))

            store.reject("b")

            assertIs<PairingSessionCreateResult.Created>(
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-new")),
            )
        }

    @Test
    fun testInitiatorSessionsBypassIncomingCapacity() =
        runTest {
            val store = newStore(maxActiveIncoming = 1)
            store.create(session("in1"))

            assertIs<PairingSessionCreateResult.Created>(
                store.create(session("out1", role = PakeRole.INITIATOR)),
            )
        }

    // endregion

    // region transition graph and evidence

    @Test
    fun testTransitionRequiresExpectedState() =
        runTest {
            val store = newStore()
            store.create(session("b"))

            val invalid =
                store.transition("b", setOf(PairingSessionState.PAKE_NEGOTIATING)) { current ->
                    current.copy(state = PairingSessionState.PEER_CONFIRMED)
                }

            val invalidState = assertIs<PairingSessionTransitionResult.InvalidState>(invalid)
            assertEquals(PairingSessionState.PIN_AVAILABLE, invalidState.actual)

            assertIs<PairingSessionTransitionResult.NotFound>(
                store.transition("missing", setOf(PairingSessionState.PIN_AVAILABLE)) { current -> current },
            )
        }

    @Test
    fun testCannotJumpToTrustedFromPinAvailable() =
        runTest {
            val store = newStore()
            store.create(session("b"))

            val result =
                store.transition("b", setOf(PairingSessionState.PIN_AVAILABLE)) { current ->
                    current.copy(state = PairingSessionState.TRUSTED)
                }

            val illegal = assertIs<PairingSessionTransitionResult.IllegalTransition>(result)
            assertEquals(PairingSessionState.PIN_AVAILABLE, illegal.from)
            assertEquals(PairingSessionState.TRUSTED, illegal.to)
            assertEquals(PairingSessionState.PIN_AVAILABLE, store.get("b")?.state)
        }

    @Test
    fun testConfirmedRequiresCryptographicEvidence() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            assertIs<PairingBeginProofResult.Proceed>(store.beginProof("b", generation = 1L))

            // A legal graph edge without transcript hash + session keys must not commit
            val result =
                store.transition("b", setOf(PairingSessionState.PAKE_NEGOTIATING)) { current ->
                    current.copy(state = PairingSessionState.PEER_CONFIRMED)
                }

            val evidence = assertIs<PairingSessionTransitionResult.EvidenceRequired>(result)
            assertEquals(PairingSessionState.PEER_CONFIRMED, evidence.target)
            assertEquals(PairingSessionState.PAKE_NEGOTIATING, store.get("b")?.state)
        }

    @Test
    fun testTrustedIsReachableOnlyThroughTheFullChain() =
        runTest {
            val store = newStore()
            store.create(session("b"))

            assertIs<PairingBeginProofResult.Proceed>(store.beginProof("b", generation = 1L))
            store.confirmPeer("b")
            assertIs<PairingSessionTransitionResult.Success>(store.beginCommit("b"))
            val trusted = store.completeTrust("b")

            assertIs<PairingSessionTransitionResult.Success>(trusted)
            assertEquals(PairingSessionState.TRUSTED, store.get("b")?.state)
            // Secrets are cleared on the trusted terminal state
            assertNull(store.get("b")?.pin)
            assertNull(store.get("b")?.sessionKeys)
        }

    @Test
    fun testTerminalStatesAreAbsorbing() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            store.reject("b")

            val result =
                store.transition("b", setOf(PairingSessionState.REJECTED)) { current ->
                    current.copy(state = PairingSessionState.PIN_AVAILABLE)
                }

            assertIs<PairingSessionTransitionResult.InvalidState>(result)
            assertEquals(PairingSessionState.REJECTED, store.get("b")?.state)
        }

    // endregion

    // region secret clearing

    @Test
    fun testTerminalTransitionClearsSecrets() =
        runTest {
            val store = newStore()
            val pin = "987654".toCharArray()
            store.create(session("b").copy(pin = pin))

            val result = store.fail("b")

            val success = assertIs<PairingSessionTransitionResult.Success>(result)
            assertNull(success.session.pin)
            assertContentEquals(CharArray(6), pin)
        }

    @Test
    fun testActionDroppingReferencesCannotBypassSecretClearing() =
        runTest {
            val store = newStore()
            val pin = "987654".toCharArray()
            val pakeSession = TrackingPakeSession()
            val keys = evidenceKeys()
            store.create(session("b").copy(pin = pin, pakeSession = pakeSession, sessionKeys = keys))

            val result =
                store.transition("b", setOf(PairingSessionState.PIN_AVAILABLE)) { current ->
                    current.copy(
                        state = PairingSessionState.FAILED,
                        pin = null,
                        sessionKeys = null,
                        pakeSession = null,
                    )
                }

            assertIs<PairingSessionTransitionResult.Success>(result)
            assertContentEquals(CharArray(6), pin)
            assertContentEquals(ByteArray(32), keys.confirmInitiator)
            assertContentEquals(ByteArray(32), keys.receipt)
            assertTrue(pakeSession.destroyed)
        }

    // endregion

    // region PIN generation lifecycle

    @Test
    fun testBeginProofFreezesGenerationBeyondPinExpiry() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            val pinExpiresAt = now + 30_000L

            val result = store.beginProof("b", generation = 1L, graceMillis = 15_000L)

            val proceed = assertIs<PairingBeginProofResult.Proceed>(result)
            assertEquals(PairingSessionState.PAKE_NEGOTIATING, proceed.session.state)
            // The grace interval extends BEYOND the PIN lifetime, not from `now`
            assertEquals(pinExpiresAt + 15_000L, proceed.session.generationFrozenUntil)
        }

    @Test
    fun testBeginProofRejectsWrongGenerationExpiredPinAndExhaustedBudget() =
        runTest {
            val store = newStore()
            store.create(session("wrong-gen"))
            assertIs<PairingBeginProofResult.WrongGeneration>(store.beginProof("wrong-gen", generation = 2L))

            store.create(session("exhausted", proofAttempts = 1))
            assertIs<PairingBeginProofResult.AttemptsExhausted>(store.beginProof("exhausted", generation = 1L))

            store.create(session("expired"))
            now += 31_000L
            assertIs<PairingBeginProofResult.PinExpired>(store.beginProof("expired", generation = 1L))
        }

    @Test
    fun testBeginProofRequiresCompleteGeneration() =
        runTest {
            val store = newStore()
            // A generation missing its published share / signed offer accepts no proof
            store.create(session("no-share", localPakeShare = null))
            store.create(session("no-offer", offerHash = null))
            store.create(session("no-pake", pakeSession = null))

            assertIs<PairingBeginProofResult.GenerationNotReady>(store.beginProof("no-share", generation = 1L))
            assertIs<PairingBeginProofResult.GenerationNotReady>(store.beginProof("no-offer", generation = 1L))
            assertIs<PairingBeginProofResult.GenerationNotReady>(store.beginProof("no-pake", generation = 1L))
        }

    @Test
    fun testCompleteProofRevalidatesGenerationAndDeadline() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            assertIs<PairingBeginProofResult.Proceed>(store.beginProof("b", generation = 1L, graceMillis = 15_000L))

            assertIs<PairingCompleteProofResult.WrongGeneration>(
                store.completeProof("b", 2L, ByteArray(32) { 9 }, evidenceKeys(), ByteArray(8) { 5 }),
            )

            // Advance past pinExpiresAt + grace: the frozen negotiation timed out
            now += 45_001L
            assertIs<PairingCompleteProofResult.DeadlineExceeded>(
                store.completeProof("b", 1L, ByteArray(32) { 9 }, evidenceKeys(), ByteArray(8) { 5 }),
            )
        }

    @Test
    fun testProofFailureInvalidatesGenerationImmediately() =
        runTest {
            val store = newStore()
            val pin = "123456".toCharArray()
            store.create(session("b").copy(pin = pin))
            assertIs<PairingBeginProofResult.Proceed>(store.beginProof("b", generation = 1L))

            val failure = store.recordProofFailure("b")

            val failed = assertIs<PairingSessionTransitionResult.Success>(failure)
            assertEquals(PairingSessionState.PIN_AVAILABLE, failed.session.state)
            assertEquals(1, failed.session.proofAttempts)
            assertNull(failed.session.pin)
            assertContentEquals(CharArray(6), pin)
            assertFalse(failed.session.isPinUsable(now))
            assertIs<PairingBeginProofResult.AttemptsExhausted>(store.beginProof("b", generation = 1L))
        }

    @Test
    fun testRotateGenerationPublishesCompleteGenerationAtomically() =
        runTest {
            val store = newStore()
            val oldPin = "123456".toCharArray()
            val oldPakeSession = TrackingPakeSession()
            store.create(session("b", proofAttempts = 1, pakeSession = oldPakeSession).copy(pin = oldPin))

            val newPakeSession = TrackingPakeSession()
            val result =
                store.rotateGeneration(
                    sessionId = "b",
                    expectedGeneration = 1L,
                    newPin = "654321".toCharArray(),
                    pinExpiresAt = now + 30_000L,
                    pakeSession = newPakeSession,
                    localPakeShare = ByteArray(8) { 6 },
                    offerHash = ByteArray(32) { 7 },
                )

            val rotated = assertIs<PairingRotateResult.Rotated>(result)
            assertEquals(2L, rotated.session.tokenGeneration)
            assertEquals(0, rotated.session.proofAttempts)
            assertContentEquals("654321".toCharArray(), rotated.session.pin!!)
            assertEquals(now + 30_000L, rotated.session.pinExpiresAt)
            // The new generation is immediately complete: share, offer, PAKE session
            assertContentEquals(ByteArray(8) { 6 }, rotated.session.localPakeShare!!)
            assertContentEquals(ByteArray(32) { 7 }, rotated.session.offerHash!!)
            assertEquals(newPakeSession, rotated.session.pakeSession)
            // Old generation destroyed
            assertContentEquals(CharArray(6), oldPin)
            assertTrue(oldPakeSession.destroyed)
            // And it accepts a proof right away — no half-published window
            assertIs<PairingBeginProofResult.Proceed>(store.beginProof("b", generation = 2L))
        }

    @Test
    fun testRotationRejectsStaleGeneration() =
        runTest {
            val store = newStore()
            store.create(session("b"))

            // Material prepared against generation 0 (stale read) must not publish
            assertIs<PairingRotateResult.StaleGeneration>(
                store.rotateGeneration(
                    sessionId = "b",
                    expectedGeneration = 0L,
                    newPin = "654321".toCharArray(),
                    pinExpiresAt = now + 30_000L,
                    pakeSession = TrackingPakeSession(),
                    localPakeShare = ByteArray(8) { 6 },
                    offerHash = ByteArray(32) { 7 },
                ),
            )
        }

    @Test
    fun testRotationIsRefusedWhileGenerationIsFrozen() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            assertIs<PairingBeginProofResult.Proceed>(store.beginProof("b", generation = 1L, graceMillis = 15_000L))

            suspend fun tryRotate(): PairingRotateResult =
                store.rotateGeneration(
                    sessionId = "b",
                    expectedGeneration = 1L,
                    newPin = "654321".toCharArray(),
                    pinExpiresAt = now + 30_000L,
                    pakeSession = TrackingPakeSession(),
                    localPakeShare = ByteArray(8) { 6 },
                    offerHash = ByteArray(32) { 7 },
                )

            assertIs<PairingRotateResult.Frozen>(tryRotate())

            // Freeze ends at pinExpiresAt + grace = 45s after creation
            now += 45_001L
            assertIs<PairingRotateResult.Rotated>(tryRotate())
        }

    @Test
    fun testRotatingOneSessionDoesNotChangeAnother() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            store.create(session("c"))

            store.rotateGeneration(
                sessionId = "b",
                expectedGeneration = 1L,
                newPin = "999999".toCharArray(),
                pinExpiresAt = now + 30_000L,
                pakeSession = TrackingPakeSession(),
                localPakeShare = ByteArray(8) { 6 },
                offerHash = ByteArray(32) { 7 },
            )

            val sessionC = store.get("c")
            assertEquals(1L, sessionC?.tokenGeneration)
            assertContentEquals("123456".toCharArray(), sessionC?.pin!!)
        }

    // endregion

    // region lifecycle and concurrency

    @Test
    fun testCompletingOneSessionDoesNotAffectAnother() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            store.create(session("c"))

            store.reject("b")

            val sessionC = store.get("c")
            assertEquals(PairingSessionState.PIN_AVAILABLE, sessionC?.state)
            assertEquals(true, sessionC?.pin != null)
        }

    @Test
    fun testConcurrentTransitionsProduceOneTerminalState() =
        runTest {
            val store = newStore()
            store.create(session("b"))
            val startGate = Mutex(locked = true)

            val results =
                coroutineScope {
                    val reject =
                        async {
                            startGate.withLock { }
                            store.reject("b")
                        }
                    val cancel =
                        async {
                            startGate.withLock { }
                            store.cancel("b")
                        }
                    startGate.unlock()
                    listOf(reject.await(), cancel.await())
                }

            assertEquals(1, results.filterIsInstance<PairingSessionTransitionResult.Success>().size)
            assertEquals(1, results.filterIsInstance<PairingSessionTransitionResult.InvalidState>().size)
            assertTrue(store.get("b")?.state?.isTerminal == true)
        }

    @Test
    fun testExpireDueSessions() =
        runTest {
            val store = newStore()
            store.create(session("soon", expiresAt = now + 1_000L))
            store.create(session("later", expiresAt = now + 100_000L))

            now += 5_000L
            val expired = store.expireDueSessions()

            assertEquals(listOf("soon"), expired.map { session -> session.sessionId })
            assertEquals(PairingSessionState.EXPIRED, store.get("soon")?.state)
            assertEquals(PairingSessionState.PIN_AVAILABLE, store.get("later")?.state)
        }

    @Test
    fun testRemoveTerminalRefusesActiveSessions() =
        runTest {
            val store = newStore()
            store.create(session("b"))

            assertFalse(store.removeTerminal("b"))

            store.reject("b")
            assertTrue(store.removeTerminal("b"))
            assertNull(store.get("b"))
        }

    @Test
    fun testPruneTerminalKeepsRecentAndActive() =
        runTest {
            val store = newStore()
            store.create(session("old"))
            store.create(session("active"))
            store.reject("old")

            now += 60_000L
            store.pruneTerminal(retentionMillis = 30_000L)

            assertNull(store.get("old"))
            assertEquals(PairingSessionState.PIN_AVAILABLE, store.get("active")?.state)
        }

    @Test
    fun testPinUsabilityWindowAndGraceFreeze() {
        val base = session("b")

        assertTrue(base.isPinUsable(now))
        assertFalse(base.isPinUsable(base.pinExpiresAt))

        val frozen = base.copy(generationFrozenUntil = base.pinExpiresAt + 15_000L)
        assertTrue(frozen.isPinUsable(base.pinExpiresAt + 1_000L))
        assertFalse(frozen.isPinUsable(base.pinExpiresAt + 15_000L))
    }

    // endregion

    // region consumed-intent replay protection

    @Test
    fun testReplayOfTerminatedIntentIsConsumed() =
        runTest {
            val store = newStore()
            val intentHash = ByteArray(32) { 7 }
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash))
            store.reject("b")

            assertIs<PairingSessionCreateResult.IntentConsumed>(
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash)),
            )
        }

    @Test
    fun testDismissingTerminalCardKeepsReplayRecord() =
        runTest {
            val store = newStore()
            val intentHash = ByteArray(32) { 7 }
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash))
            store.reject("b")

            assertTrue(store.removeTerminal("b"))

            assertIs<PairingSessionCreateResult.IntentConsumed>(
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash)),
            )
        }

    @Test
    fun testTrustedIntentCannotBeReplayed() =
        runTest {
            val store = newStore()
            val intentHash = ByteArray(32) { 7 }
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash))
            store.beginProof("b", generation = 1L)
            store.confirmPeer("b")
            store.beginCommit("b")
            store.completeTrust("b")

            assertIs<PairingSessionCreateResult.IntentConsumed>(
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash)),
            )
        }

    @Test
    fun testFreshIntentFromSamePeerIsAllowedAfterTerminalState() =
        runTest {
            val store = newStore()
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = ByteArray(32) { 7 }))
            store.reject("b")

            assertIs<PairingSessionCreateResult.Created>(
                store.create(
                    session("b2", fingerprint = "fp-x", requestId = "req-2", intentHash = ByteArray(32) { 8 }),
                ),
            )
        }

    @Test
    fun testTombstoneExpiresAfterTtl() =
        runTest {
            val store = newStore(intentTombstoneTtlMillis = 1_000L)
            val intentHash = ByteArray(32) { 7 }
            store.create(session("b", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash))
            store.reject("b")

            now += 1_001L
            assertIs<PairingSessionCreateResult.Created>(
                store.create(session("b2", fingerprint = "fp-x", requestId = "req-1", intentHash = intentHash)),
            )
        }

    @Test
    fun testTombstoneIndexIsBounded() =
        runTest {
            val store = newStore(maxIntentTombstones = 2)
            repeat(3) { index ->
                store.create(session("s$index", intentHash = ByteArray(32) { index.toByte() }))
                now += 1
                store.reject("s$index")
                now += 1
            }

            assertIs<PairingSessionCreateResult.Created>(
                store.create(
                    session("r0", fingerprint = "fp-s0", requestId = "req-s0", intentHash = ByteArray(32) { 0 }),
                ),
            )
            assertIs<PairingSessionCreateResult.IntentConsumed>(
                store.create(
                    session("r2", fingerprint = "fp-s2", requestId = "req-s2", intentHash = ByteArray(32) { 2 }),
                ),
            )
        }

    // endregion

    @Test
    fun testUiFlowExposesSanitizedStateOnly() =
        runTest {
            val store = newStore()
            store.create(session("b", fingerprint = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"))

            val uiState = store.uiSessionsFlow.value.single()

            assertEquals("b", uiState.sessionId)
            assertEquals("123456", uiState.pin)
            assertEquals("aabbccdd", uiState.peerKeyFingerprintDisplay)
            assertEquals(PairingSessionState.PIN_AVAILABLE, uiState.state)
        }
}
