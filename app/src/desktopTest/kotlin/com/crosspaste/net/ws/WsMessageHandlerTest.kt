package com.crosspaste.net.ws

import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.utils.getJsonUtils
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WsMessageHandlerTest {

    private class Fixture(
        authenticatedPeer: String,
        allowReceive: Boolean = true,
        appReceiveEnabled: Boolean = true,
        writeResult: Result<Unit?> = Result.success(Unit),
    ) {
        val appControl = mockk<AppControl>(relaxed = true)
        val pasteboardService = mockk<PasteboardService>(relaxed = true)
        val syncHandler = mockk<SyncHandler>()
        val syncRoutingApi = mockk<SyncRoutingApi>()
        val wsSessionManager = mockk<WsSessionManager>(relaxed = true)
        val receivedPastes = mutableListOf<PasteData>()
        val responses = mutableListOf<WsEnvelope>()

        val handler: WsMessageHandler

        init {
            every { syncHandler.currentSyncRuntimeInfo } returns
                createConnectedSyncRuntimeInfo(authenticatedPeer).copy(allowReceive = allowReceive)
            every { syncRoutingApi.getSyncHandler(authenticatedPeer) } returns syncHandler
            coEvery { appControl.isReceiveEnabled() } returns appReceiveEnabled
            coEvery { pasteboardService.tryWriteRemotePasteboard(capture(receivedPastes), any()) } returns writeResult
            coEvery { wsSessionManager.send(authenticatedPeer, capture(responses)) } returns true

            handler =
                WsMessageHandler(
                    lazyAppControl = lazyOf(appControl),
                    lazyCacheManager = lazyOf(mockk<CacheManager>(relaxed = true)),
                    lazyPasteDao = lazyOf(mockk<PasteDao>(relaxed = true)),
                    lazyPasteboardService = lazyOf(pasteboardService),
                    lazySyncRoutingApi = lazyOf(syncRoutingApi),
                    secureStore = mockk<SecureStore>(relaxed = true),
                    userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true),
                    wsSessionManager = wsSessionManager,
                )
        }
    }

    private fun pastePush(
        bodyAppInstanceId: String = "spoofed-peer",
        requestId: String? = null,
    ): WsEnvelope {
        val body =
            PasteData(
                appInstanceId = bodyAppInstanceId,
                pasteCollection = PasteCollection(emptyList()),
                pasteType = PasteType.TEXT_TYPE.type,
                size = 4,
                hash = "hash",
            )
        return WsEnvelope(
            type = WsMessageType.PASTE_PUSH,
            payload = getJsonUtils().JSON.encodeToString(body).encodeToByteArray(),
            requestId = requestId,
        )
    }

    @Test
    fun pastePush_successBindsPeerAndAcknowledgesAfterIngestion() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture = Fixture(authenticatedPeer)

            fixture.handler.handleMessage(authenticatedPeer, pastePush(requestId = "request-1"))

            assertEquals(1, fixture.receivedPastes.size)
            assertEquals(authenticatedPeer, fixture.receivedPastes.single().appInstanceId)
            assertTrue(fixture.receivedPastes.single().remote)
            assertEquals(
                WsEnvelope(WsMessageType.PASTE_PUSH_ACK, requestId = "request-1"),
                fixture.responses.single(),
            )
            coVerifyOrder {
                fixture.pasteboardService.tryWriteRemotePasteboard(any(), any())
                fixture.wsSessionManager.send(authenticatedPeer, any())
                fixture.appControl.completeReceiveOperation()
            }
        }

    @Test
    fun pastePush_ingestionFailureReturnsCorrelatedError() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture =
                Fixture(
                    authenticatedPeer = authenticatedPeer,
                    writeResult = Result.failure(IllegalStateException("database unavailable")),
                )

            fixture.handler.handleMessage(authenticatedPeer, pastePush(requestId = "request-1"))

            assertEquals(WsMessageType.ERROR, fixture.responses.single().type)
            assertEquals("request-1", fixture.responses.single().requestId)
            coVerify(exactly = 0) { fixture.appControl.completeReceiveOperation() }
        }

    @Test
    fun pastePush_ingestionRejectionReturnsCorrelatedError() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture =
                Fixture(
                    authenticatedPeer = authenticatedPeer,
                    writeResult = Result.success(null),
                )

            fixture.handler.handleMessage(authenticatedPeer, pastePush(requestId = "request-1"))

            assertEquals(WsMessageType.ERROR, fixture.responses.single().type)
            assertEquals("request-1", fixture.responses.single().requestId)
            coVerify(exactly = 0) { fixture.appControl.completeReceiveOperation() }
        }

    @Test
    fun pastePush_receivingDisabledReturnsCorrelatedErrorWithoutIngestion() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture = Fixture(authenticatedPeer, allowReceive = false)

            fixture.handler.handleMessage(authenticatedPeer, pastePush(requestId = "request-1"))

            assertTrue(fixture.receivedPastes.isEmpty())
            assertEquals(WsMessageType.ERROR, fixture.responses.single().type)
            assertEquals("request-1", fixture.responses.single().requestId)
        }

    @Test
    fun pastePush_legacyRequestDoesNotSendResponse() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture = Fixture(authenticatedPeer)

            fixture.handler.handleMessage(authenticatedPeer, pastePush())

            assertEquals(1, fixture.receivedPastes.size)
            assertTrue(fixture.responses.isEmpty())
        }

    @Test
    fun heartbeat_echoesRequestIdInAck() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture = Fixture(authenticatedPeer)

            fixture.handler.handleMessage(
                authenticatedPeer,
                WsEnvelope(WsMessageType.HEARTBEAT, requestId = "hb-1"),
            )

            assertEquals(
                WsEnvelope(WsMessageType.HEARTBEAT_ACK, requestId = "hb-1"),
                fixture.responses.single(),
            )
        }

    @Test
    fun heartbeatAck_withRequestId_completesPendingRequest() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val fixture = Fixture(authenticatedPeer)
            val ack = WsEnvelope(WsMessageType.HEARTBEAT_ACK, requestId = "hb-2")

            fixture.handler.handleMessage(authenticatedPeer, ack)

            coVerify(exactly = 1) { fixture.wsSessionManager.completePendingRequest("hb-2", ack) }
        }

    @Test
    fun dispatchWsSessionMessages_heartbeatNotBlockedBySlowPastePush() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val wsSessionManager = mockk<WsSessionManager>(relaxed = true)
            val pasteStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releasePaste = kotlinx.coroutines.CompletableDeferred<Unit>()
            val heartbeatAcked = kotlinx.coroutines.CompletableDeferred<WsEnvelope>()
            val wsMessageHandler = mockk<WsMessageHandler>()
            coEvery { wsMessageHandler.handleMessage(authenticatedPeer, any()) } coAnswers {
                val env = secondArg<WsEnvelope>()
                when (env.type) {
                    WsMessageType.PASTE_PUSH -> {
                        pasteStarted.complete(Unit)
                        releasePaste.await()
                    }
                    WsMessageType.HEARTBEAT -> {
                        val ack = WsEnvelope(WsMessageType.HEARTBEAT_ACK, requestId = env.requestId)
                        wsSessionManager.send(authenticatedPeer, ack)
                        heartbeatAcked.complete(ack)
                    }
                }
            }
            val session =
                WsSession(
                    session = mockk(relaxed = true),
                    remoteAppInstanceId = authenticatedPeer,
                )
            var readLoopFinished = false

            dispatchWsSessionMessages(
                appInstanceId = authenticatedPeer,
                wsSession = session,
                wsMessageHandler = wsMessageHandler,
                onReadLoopFinished = { readLoopFinished = true },
            ) { dispatch ->
                dispatch(WsEnvelope(WsMessageType.PASTE_PUSH, requestId = "p-1"))
                pasteStarted.await()

                // Heartbeat arrives while PASTE_PUSH is still blocked; it must complete immediately.
                dispatch(WsEnvelope(WsMessageType.HEARTBEAT, requestId = "hb-busy"))
                val ack = heartbeatAcked.await()
                assertEquals("hb-busy", ack.requestId)
                assertEquals(2L, session.inboundActivitySnapshot())

                releasePaste.complete(Unit)
            }

            assertTrue(readLoopFinished)
            coVerify(exactly = 1) {
                wsSessionManager.send(
                    authenticatedPeer,
                    WsEnvelope(WsMessageType.HEARTBEAT_ACK, requestId = "hb-busy"),
                )
            }
        }

    @Test
    fun dispatchWsSessionMessages_closesSessionWhenPendingPayloadBudgetExceeded() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
            val handledRequestIds = mutableListOf<String?>()
            val wsMessageHandler = mockk<WsMessageHandler>()
            coEvery { wsMessageHandler.handleMessage(authenticatedPeer, any()) } coAnswers {
                val env = secondArg<WsEnvelope>()
                handledRequestIds.add(env.requestId)
                if (env.requestId == "p-1") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            }
            val innerSession = mockk<WebSocketSession>(relaxed = true)
            val session =
                WsSession(
                    session = innerSession,
                    remoteAppInstanceId = authenticatedPeer,
                )
            var readLoopFinished = false

            val dispatcherJob =
                launch {
                    dispatchWsSessionMessages(
                        appInstanceId = authenticatedPeer,
                        wsSession = session,
                        wsMessageHandler = wsMessageHandler,
                        onReadLoopFinished = { readLoopFinished = true },
                        maxPendingPayloadBytes = 100L,
                    ) { dispatch ->
                        dispatch(WsEnvelope(WsMessageType.PASTE_PUSH, payload = ByteArray(60), requestId = "p-1"))
                        firstStarted.await()
                        // Second payload would push queued bytes to 120 > 100; must close session and not enqueue.
                        dispatch(WsEnvelope(WsMessageType.PASTE_PUSH, payload = ByteArray(60), requestId = "p-2"))
                    }
                }

            firstStarted.await()
            releaseFirst.complete(Unit)
            dispatcherJob.join()

            assertTrue(readLoopFinished)
            assertEquals(listOf<String?>("p-1"), handledRequestIds)
            coVerify(exactly = 1) {
                innerSession.send(
                    match<Frame.Close> { frame ->
                        frame.readReason()?.knownReason == CloseReason.Codes.TRY_AGAIN_LATER
                    },
                )
            }
        }

    @Test
    fun dispatchWsSessionMessages_releasesBudgetAfterProcessingMessage() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val firstDone = kotlinx.coroutines.CompletableDeferred<Unit>()
            val handledRequestIds = mutableListOf<String?>()
            val wsMessageHandler = mockk<WsMessageHandler>()
            coEvery { wsMessageHandler.handleMessage(authenticatedPeer, any()) } coAnswers {
                val env = secondArg<WsEnvelope>()
                handledRequestIds.add(env.requestId)
                if (env.requestId == "p-1") {
                    firstDone.complete(Unit)
                }
            }
            val innerSession = mockk<io.ktor.websocket.WebSocketSession>(relaxed = true)
            val session =
                WsSession(
                    session = innerSession,
                    remoteAppInstanceId = authenticatedPeer,
                )

            dispatchWsSessionMessages(
                appInstanceId = authenticatedPeer,
                wsSession = session,
                wsMessageHandler = wsMessageHandler,
                onReadLoopFinished = {},
                maxPendingPayloadBytes = 100L,
            ) { dispatch ->
                dispatch(WsEnvelope(WsMessageType.PASTE_PUSH, payload = ByteArray(80), requestId = "p-1"))
                firstDone.await()
                // Yield so worker's finally block releases the 80 bytes before next message arrives.
                kotlinx.coroutines.yield()
                dispatch(WsEnvelope(WsMessageType.PASTE_PUSH, payload = ByteArray(80), requestId = "p-2"))
            }

            assertEquals(listOf<String?>("p-1", "p-2"), handledRequestIds)
            coVerify(exactly = 0) { innerSession.send(any<io.ktor.websocket.Frame.Close>()) }
        }
}
