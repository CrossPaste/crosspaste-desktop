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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
}
