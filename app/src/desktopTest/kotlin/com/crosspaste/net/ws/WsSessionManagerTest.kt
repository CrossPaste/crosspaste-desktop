package com.crosspaste.net.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WsSessionManagerTest {

    private fun fakeSession(active: Boolean = true): WsSession {
        val job = Job().apply { if (!active) cancel() }
        val inner: WebSocketSession =
            mockk(relaxed = true) {
                every { coroutineContext } returns job
            }
        return WsSession(inner, "remote")
    }

    @Test
    fun notifySessionClosed_presentSession_invokesCallbackOnce() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val session = fakeSession()
            mgr.registerSession("A", session)

            mgr.notifySessionClosed("A", session)

            assertEquals(listOf("A"), fired)
        }

    @Test
    fun notifySessionClosed_absentSession_doesNotInvokeCallback() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }

            mgr.notifySessionClosed("missing", fakeSession())

            assertEquals(emptyList(), fired)
        }

    @Test
    fun notifySessionClosed_calledTwice_invokesCallbackOnce() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val session = fakeSession()
            mgr.registerSession("A", session)

            mgr.notifySessionClosed("A", session)
            mgr.notifySessionClosed("A", session)

            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun notifySessionClosed_replacedSession_doesNotRemoveNewEntry() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val oldSession = fakeSession()
            val newSession = fakeSession()
            mgr.registerSession("A", oldSession)
            mgr.registerSession("A", newSession)

            mgr.notifySessionClosed("A", oldSession)

            assertEquals(emptyList(), fired)
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun registerSession_invokesOnSessionOpened() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionOpened { fired.add(it) }

            mgr.registerSession("A", fakeSession())

            assertEquals(listOf("A"), fired)
        }

    @Test
    fun registerSession_replacingSession_invokesOnSessionOpenedAgain() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionOpened { fired.add(it) }

            mgr.registerSession("A", fakeSession())
            mgr.registerSession("A", fakeSession())

            assertEquals(listOf("A", "A"), fired)
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun unregisterSession_doesNotInvokeCallback() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            mgr.registerSession("A", fakeSession())

            mgr.unregisterSession("A")

            assertEquals(emptyList(), fired)
        }

    @Test
    fun probe_unknownAppInstanceId_returnsFalse() =
        runTest {
            val mgr = WsSessionManager()

            assertFalse(mgr.probe("missing"))
        }

    @Test
    fun probe_sessionNotActive_firesCallbackAndReturnsFalse() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            mgr.registerSession("A", fakeSession(active = false))

            assertFalse(mgr.probe("A"))
            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun probe_pingFails_firesCallbackAndReturnsFalse() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            coEvery { inner.send(any<Frame.Ping>()) } throws IOException("closed")
            mgr.registerSession("A", WsSession(inner, "remote"))

            assertFalse(mgr.probe("A"))
            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun probe_pingSucceeds_returnsTrueAndSessionStillRegistered() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            coEvery { inner.send(any<Frame.Ping>()) } just Runs
            mgr.registerSession("A", WsSession(inner, "remote"))

            assertTrue(mgr.probe("A"))
            assertEquals(emptyList(), fired)
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun closeAll_closesAndUnregistersAllSessions() =
        runTest {
            val mgr = WsSessionManager()
            val innerA: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                }
            val innerB: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                }
            mgr.registerSession("A", WsSession(innerA, "remote"))
            mgr.registerSession("B", WsSession(innerB, "remote"))

            mgr.closeAll()

            assertFalse(mgr.isConnected("A"))
            assertFalse(mgr.isConnected("B"))
            coVerify(exactly = 1) { innerA.send(any<Frame.Close>()) }
            coVerify(exactly = 1) { innerB.send(any<Frame.Close>()) }
        }

    @Test
    fun sendPastePush_legacySessionRejectsAboveSingleFrameLimit() =
        runTest {
            val mgr = WsSessionManager()
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            mgr.registerSession("A", WsSession(inner, "remote"))
            val envelope = WsEnvelope(type = WsMessageType.PASTE_PUSH, payload = ByteArray(65))

            val result =
                mgr.sendPastePush(
                    appInstanceId = "A",
                    envelope = envelope,
                    singleFramePayloadLimit = 64,
                    chunkedPayloadLimit = 128,
                )

            assertEquals(WsPayloadSendResult.PayloadTooLarge(65, 64), result)
            coVerify(exactly = 0) { inner.send(any<Frame>()) }
        }

    @Test
    fun sendPastePush_chunkCapableSessionUsesChunkedLimitAndSends() =
        runTest {
            val mgr = WsSessionManager()
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            mgr.registerSession(
                "A",
                WsSession(inner, "remote", peerSupportsChunkedPayload = true),
            )
            val envelope = WsEnvelope(type = WsMessageType.PASTE_PUSH, payload = ByteArray(65))

            val result =
                mgr.sendPastePush(
                    appInstanceId = "A",
                    envelope = envelope,
                    singleFramePayloadLimit = 64,
                    chunkedPayloadLimit = 128,
                )

            assertEquals(WsPayloadSendResult.Sent, result)
            coVerify(exactly = 1) { inner.send(any<Frame.Text>()) }
            coVerify(exactly = 1) { inner.send(any<Frame.Binary>()) }
        }

    @Test
    fun sendPastePush_chunkCapableSessionRejectsAboveChunkedLimit() =
        runTest {
            val mgr = WsSessionManager()
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            mgr.registerSession(
                "A",
                WsSession(inner, "remote", peerSupportsChunkedPayload = true),
            )
            val envelope = WsEnvelope(type = WsMessageType.PASTE_PUSH, payload = ByteArray(129))

            val result =
                mgr.sendPastePush(
                    appInstanceId = "A",
                    envelope = envelope,
                    singleFramePayloadLimit = 64,
                    chunkedPayloadLimit = 128,
                )

            assertEquals(WsPayloadSendResult.PayloadTooLarge(129, 128), result)
            coVerify(exactly = 0) { inner.send(any<Frame>()) }
        }

    @Test
    fun sendPastePush_legacyPeerReturnsAfterSendWithoutRequestId() =
        runTest {
            val mgr = WsSessionManager()
            val sentHeader = slot<Frame.Text>()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(capture(sentHeader)) } just Runs
                }
            mgr.registerSession("A", WsSession(inner, "remote"))

            val result =
                mgr.sendPastePush(
                    appInstanceId = "A",
                    envelope = WsEnvelope(WsMessageType.PASTE_PUSH, byteArrayOf(1)),
                    singleFramePayloadLimit = 64,
                    chunkedPayloadLimit = 128,
                )

            assertEquals(WsPayloadSendResult.Sent, result)
            val header =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON
                    .decodeFromString<WsEnvelopeHeader>(sentHeader.captured.readText())
            assertEquals(null, header.requestId)
        }

    @Test
    fun sendPastePush_ackCapablePeerWaitsForMatchingAck() =
        runTest {
            val mgr = WsSessionManager()
            val sentFrames = mutableListOf<Frame>()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(capture(sentFrames)) } just Runs
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.PASTE_PUSH_ACK),
                ),
            )

            val result =
                async {
                    mgr.sendPastePush(
                        appInstanceId = "A",
                        envelope = WsEnvelope(WsMessageType.PASTE_PUSH, byteArrayOf(1)),
                        singleFramePayloadLimit = 64,
                        chunkedPayloadLimit = 128,
                    )
                }
            runCurrent()

            val requestId =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON
                    .decodeFromString<WsEnvelopeHeader>(sentFrames.filterIsInstance<Frame.Text>().single().readText())
                    .requestId
            requireNotNull(requestId)
            assertTrue(
                mgr.completePendingRequest(
                    requestId,
                    WsEnvelope(WsMessageType.PASTE_PUSH_ACK, requestId = requestId),
                ),
            )
            assertEquals(WsPayloadSendResult.Sent, result.await())
        }

    @Test
    fun sendPastePush_ackCapablePeerReportsReceiverErrorAsRejected() =
        runTest {
            val mgr = WsSessionManager()
            val sentFrames = mutableListOf<Frame>()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(capture(sentFrames)) } just Runs
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.PASTE_PUSH_ACK),
                ),
            )

            val result =
                async {
                    mgr.sendPastePush(
                        appInstanceId = "A",
                        envelope = WsEnvelope(WsMessageType.PASTE_PUSH, byteArrayOf(1)),
                        singleFramePayloadLimit = 64,
                        chunkedPayloadLimit = 128,
                    )
                }
            runCurrent()

            val requestId =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON
                    .decodeFromString<WsEnvelopeHeader>(sentFrames.filterIsInstance<Frame.Text>().single().readText())
                    .requestId
            requireNotNull(requestId)
            mgr.completePendingRequest(
                requestId,
                WsEnvelope(
                    type = WsMessageType.ERROR,
                    payload = "rejected".encodeToByteArray(),
                    requestId = requestId,
                ),
            )
            assertEquals(WsPayloadSendResult.Rejected("rejected"), result.await())
        }

    @Test
    fun sendPastePush_ackCapablePeerReportsAckTimeoutAsFailure() =
        runTest {
            val mgr = WsSessionManager()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.PASTE_PUSH_ACK),
                ),
            )

            val result =
                async {
                    mgr.sendPastePush(
                        appInstanceId = "A",
                        envelope = WsEnvelope(WsMessageType.PASTE_PUSH, byteArrayOf(1)),
                        singleFramePayloadLimit = 64,
                        chunkedPayloadLimit = 128,
                        ackTimeoutMs = 100,
                    )
                }
            runCurrent()
            advanceTimeBy(101)
            runCurrent()

            assertEquals(WsPayloadSendResult.Failed, result.await())
        }

    @Test
    fun sendPastePush_ackCapablePeerRejectsOversizeBeforeWaitingForAck() =
        runTest {
            val mgr = WsSessionManager()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.PASTE_PUSH_ACK),
                ),
            )

            val result =
                mgr.sendPastePush(
                    appInstanceId = "A",
                    envelope = WsEnvelope(WsMessageType.PASTE_PUSH, ByteArray(65)),
                    singleFramePayloadLimit = 64,
                    chunkedPayloadLimit = 64,
                )

            assertEquals(WsPayloadSendResult.PayloadTooLarge(65, 64), result)
            coVerify(exactly = 0) { inner.send(any<Frame>()) }
        }

    @Test
    fun send_callerCancelled_rethrowsWithoutClosingHealthySession() =
        runTest {
            val mgr = WsSessionManager()
            val closed = mutableListOf<String>()
            mgr.setOnSessionClosed { closed.add(it) }
            val sendStarted = CompletableDeferred<Unit>()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(any<Frame>()) } coAnswers {
                        sendStarted.complete(Unit)
                        awaitCancellation()
                    }
                }
            mgr.registerSession("A", WsSession(inner, "remote"))

            var caughtCancellation: kotlinx.coroutines.CancellationException? = null
            val callerJob =
                launch {
                    try {
                        mgr.send("A", WsEnvelope(WsMessageType.HEARTBEAT))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        caughtCancellation = e
                        throw e
                    }
                }
            sendStarted.await()
            callerJob.cancel()
            callerJob.join()

            assertTrue(caughtCancellation != null)
            assertEquals(emptyList(), closed)
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun send_channelCancelledWhileCallerActive_closesSessionAndReturnsFalse() =
        runTest {
            val mgr = WsSessionManager()
            val closed = mutableListOf<String>()
            mgr.setOnSessionClosed { closed.add(it) }
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    // Simulates Ktor outgoing channel cancellation while caller coroutine is still active.
                    coEvery { send(any<Frame>()) } throws kotlinx.coroutines.CancellationException("channel cancelled")
                }
            mgr.registerSession("A", WsSession(inner, "remote"))

            val sent = mgr.send("A", WsEnvelope(WsMessageType.NOTIFY_REMOVE))

            assertFalse(sent)
            assertEquals(listOf("A"), closed)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun closeAll_failsInFlightRequestsAndPastePushAckImmediately() =
        runTest {
            val mgr = WsSessionManager()
            val innerA: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                }
            val innerB: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                }
            mgr.registerSession(
                "A",
                WsSession(innerA, "remote-a", peerCapabilities = setOf(WsCapability.PASTE_PUSH_ACK)),
            )
            mgr.registerSession("B", WsSession(innerB, "remote-b"))

            val pushDeferred =
                async {
                    mgr.sendPastePush(
                        appInstanceId = "A",
                        envelope = WsEnvelope(WsMessageType.PASTE_PUSH, byteArrayOf(1)),
                        singleFramePayloadLimit = 64,
                        chunkedPayloadLimit = 128,
                        ackTimeoutMs = 30_000,
                    )
                }
            val pullDeferred =
                async {
                    runCatching {
                        mgr.request(
                            appInstanceId = "B",
                            envelope = WsEnvelope(WsMessageType.FILE_PULL_REQUEST),
                            timeoutMs = 30_000,
                        )
                    }
                }
            runCurrent()

            // No time advance — closeAll must unblock waiters immediately rather than waiting 30s.
            mgr.closeAll()
            runCurrent()

            assertEquals(WsPayloadSendResult.Failed, pushDeferred.await())
            assertTrue(pullDeferred.await().exceptionOrNull() is WsSessionClosedException)
        }

    @Test
    fun notifySessionClosed_failsInFlightRequestsForClosedSessionOnly() =
        runTest {
            val mgr = WsSessionManager()
            val sessionA = fakeSession()
            val sessionB = fakeSession()
            mgr.registerSession("A", sessionA)
            mgr.registerSession("B", sessionB)

            val requestA =
                async {
                    runCatching {
                        mgr.request("A", WsEnvelope(WsMessageType.FILE_PULL_REQUEST), timeoutMs = 30_000)
                    }
                }
            val requestB =
                async {
                    runCatching {
                        mgr.request("B", WsEnvelope(WsMessageType.FILE_PULL_REQUEST), timeoutMs = 30_000)
                    }
                }
            runCurrent()

            mgr.notifySessionClosed("A", sessionA)
            runCurrent()

            assertTrue(requestA.isCompleted)
            assertTrue(requestA.await().exceptionOrNull() is WsSessionClosedException)
            assertFalse(requestB.isCompleted)

            mgr.closeSession("B")
            runCurrent()
            assertTrue(requestB.await().exceptionOrNull() is WsSessionClosedException)
        }

    @Test
    fun registerSession_replacingSession_failsOldSessionPendingRequestsImmediately() =
        runTest {
            val mgr = WsSessionManager()
            val oldSession = fakeSession()
            val newSession = fakeSession()
            mgr.registerSession("A", oldSession)

            val oldRequest =
                async {
                    runCatching {
                        mgr.request("A", WsEnvelope(WsMessageType.FILE_PULL_REQUEST), timeoutMs = 30_000)
                    }
                }
            runCurrent()

            mgr.registerSession("A", newSession)
            runCurrent()

            assertTrue(oldRequest.isCompleted)
            assertTrue(oldRequest.await().exceptionOrNull() is WsSessionClosedException)
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun probe_heartbeatAckCapablePeer_waitsForCorrelatedAck() =
        runTest {
            val mgr = WsSessionManager()
            val sentFrames = mutableListOf<Frame>()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(capture(sentFrames)) } just Runs
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.HEARTBEAT_ACK),
                ),
            )

            val probeDeferred = async { mgr.probe("A", timeoutMs = 1_000) }
            runCurrent()

            val header =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON
                    .decodeFromString<WsEnvelopeHeader>(sentFrames.filterIsInstance<Frame.Text>().single().readText())
            assertEquals(WsMessageType.HEARTBEAT, header.type)
            val requestId = requireNotNull(header.requestId)
            assertFalse(probeDeferred.isCompleted)

            assertTrue(
                mgr.completePendingRequest(
                    requestId,
                    WsEnvelope(WsMessageType.HEARTBEAT_ACK, requestId = requestId),
                ),
            )
            assertTrue(probeDeferred.await())
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun probe_heartbeatAckCapablePeer_halfOpenTimesOutAndClosesSession() =
        runTest {
            val mgr = WsSessionManager()
            val closed = mutableListOf<String>()
            mgr.setOnSessionClosed { closed.add(it) }
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    // Write succeeds into kernel buffer (half-open TCP), but no ACK ever arrives.
                    coEvery { send(any<Frame>()) } just Runs
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.HEARTBEAT_ACK),
                ),
            )

            val probeDeferred = async { mgr.probe("A", timeoutMs = 100) }
            runCurrent()
            advanceTimeBy(101)
            runCurrent()

            assertFalse(probeDeferred.await())
            assertEquals(listOf("A"), closed)
            assertFalse(mgr.isConnected("A"))
            coVerify(exactly = 1) { inner.send(match { it is Frame.Close }) }
        }

    @Test
    fun notifySessionClosed_removesSessionBeforeFailingPendingRequests() =
        runTest {
            val mgr = WsSessionManager()
            val session = fakeSession()
            mgr.registerSession("A", session)

            var secondRequestFailedImmediately = false
            val firstRequest =
                launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    val firstResult =
                        runCatching {
                            mgr.request("A", WsEnvelope(WsMessageType.FILE_PULL_REQUEST), timeoutMs = 30_000)
                        }
                    assertTrue(firstResult.exceptionOrNull() is WsSessionClosedException)
                    // Immediately enqueue a follow-up request once woken by failForSession:
                    // the closed session must already be removed so this cannot hang for 30s.
                    val secondResult =
                        runCatching {
                            mgr.request("A", WsEnvelope(WsMessageType.FILE_PULL_REQUEST), timeoutMs = 30_000)
                        }
                    secondRequestFailedImmediately = secondResult.isFailure
                }
            runCurrent()

            mgr.notifySessionClosed("A", session)
            runCurrent()

            assertTrue(firstRequest.isCompleted)
            assertTrue(secondRequestFailedImmediately)
        }

    @Test
    fun probe_busySessionWithInFlightPastePush_doesNotInterruptActiveSync() =
        runTest {
            val mgr = WsSessionManager()
            val sentFrames = mutableListOf<Frame>()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(capture(sentFrames)) } just Runs
                }
            mgr.registerSession(
                "A",
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.PASTE_PUSH_ACK, WsCapability.HEARTBEAT_ACK),
                ),
            )

            val pushDeferred =
                async {
                    mgr.sendPastePush(
                        appInstanceId = "A",
                        envelope = WsEnvelope(WsMessageType.PASTE_PUSH, byteArrayOf(1, 2, 3)),
                        singleFramePayloadLimit = 64,
                        chunkedPayloadLimit = 128,
                        ackTimeoutMs = 30_000,
                    )
                }
            runCurrent()

            // Session already has an in-flight PASTE_PUSH awaiting ACK: probe must not close it.
            assertTrue(mgr.probe("A", timeoutMs = 100))
            assertTrue(mgr.isConnected("A"))
            assertFalse(pushDeferred.isCompleted)

            val pushHeader =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON
                    .decodeFromString<WsEnvelopeHeader>(sentFrames.filterIsInstance<Frame.Text>().single().readText())
            assertEquals(WsMessageType.PASTE_PUSH, pushHeader.type)
            val requestId = requireNotNull(pushHeader.requestId)
            assertTrue(
                mgr.completePendingRequest(
                    requestId,
                    WsEnvelope(WsMessageType.PASTE_PUSH_ACK, requestId = requestId),
                ),
            )
            assertEquals(WsPayloadSendResult.Sent, pushDeferred.await())
        }

    @Test
    fun probe_timeoutWhileInboundTrafficObserved_keepsSessionAlive() =
        runTest {
            val mgr = WsSessionManager()
            val closed = mutableListOf<String>()
            mgr.setOnSessionClosed { closed.add(it) }
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(any<Frame>()) } just Runs
                }
            val session =
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.HEARTBEAT_ACK),
                )
            mgr.registerSession("A", session)

            val probeDeferred = async { mgr.probe("A", timeoutMs = 100) }
            runCurrent()

            // Simulate peer sending inbound traffic while HEARTBEAT_ACK is delayed.
            session.recordInboundActivity()
            advanceTimeBy(101)
            runCurrent()

            assertTrue(probeDeferred.await())
            assertEquals(emptyList(), closed)
            assertTrue(mgr.isConnected("A"))
        }

    @Test
    fun probe_chunkedInboundMessageInProgress_keepsSessionAlive() =
        runTest {
            val mgr = WsSessionManager()
            val closed = mutableListOf<String>()
            mgr.setOnSessionClosed { closed.add(it) }
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns Job()
                    coEvery { send(any<Frame>()) } just Runs
                }
            val session =
                WsSession(
                    inner,
                    "remote",
                    peerCapabilities = setOf(WsCapability.HEARTBEAT_ACK),
                )
            mgr.registerSession("A", session)

            val incoming = kotlinx.coroutines.channels.Channel<Frame>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val receiveDeferred =
                async {
                    receiveWsEnvelope(incoming, onFrameReceived = session::recordInboundActivity)
                }

            val headerJson =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON
                    .encodeToString(
                        WsEnvelopeHeader(
                            type = WsMessageType.PASTE_PUSH,
                            hasPayload = true,
                            payloadChunkCount = 3,
                        ),
                    )
            incoming.send(Frame.Text(headerJson))
            incoming.send(Frame.Binary(true, byteArrayOf(1, 2)))
            runCurrent()

            val probeDeferred = async { mgr.probe("A", timeoutMs = 100) }
            runCurrent()

            // A second chunk arrives within the probe timeout window while assembly is still incomplete.
            incoming.send(Frame.Binary(true, byteArrayOf(3, 4)))
            runCurrent()

            advanceTimeBy(101)
            runCurrent()

            assertTrue(probeDeferred.await())
            assertEquals(emptyList(), closed)
            assertTrue(mgr.isConnected("A"))
            coVerify(exactly = 0) { inner.send(match { it is Frame.Close }) }

            // Final chunk completes the message cleanly.
            incoming.send(Frame.Binary(true, byteArrayOf(5, 6)))
            val assembled = receiveDeferred.await()
            assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6), assembled?.envelope?.payload?.toList())
        }
}
