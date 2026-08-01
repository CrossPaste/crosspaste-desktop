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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WsMessageHandlerTest {

    @Test
    fun `paste push binds body identity to websocket peer`() =
        runTest {
            val authenticatedPeer = "authenticated-peer"
            val appControl = mockk<AppControl>(relaxed = true)
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            val syncHandler = mockk<SyncHandler>()
            val syncRoutingApi = mockk<SyncRoutingApi>()
            val receivedPaste = slot<PasteData>()

            every { syncHandler.currentSyncRuntimeInfo } returns
                createConnectedSyncRuntimeInfo(authenticatedPeer).copy(allowReceive = true)
            every { syncRoutingApi.getSyncHandler(authenticatedPeer) } returns syncHandler
            coEvery { appControl.isReceiveEnabled() } returns true
            coEvery { pasteboardService.tryWriteRemotePasteboard(capture(receivedPaste)) } returns Result.success(Unit)

            val handler =
                WsMessageHandler(
                    lazyAppControl = lazyOf(appControl),
                    lazyCacheManager = lazyOf(mockk<CacheManager>(relaxed = true)),
                    lazyPasteDao = lazyOf(mockk<PasteDao>(relaxed = true)),
                    lazyPasteboardService = lazyOf(pasteboardService),
                    lazySyncRoutingApi = lazyOf(syncRoutingApi),
                    secureStore = mockk<SecureStore>(relaxed = true),
                    userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true),
                    wsPendingRequests = mockk<WsPendingRequests>(relaxed = true),
                    wsSessionManager = mockk<WsSessionManager>(relaxed = true),
                    scope = this,
                )
            val body =
                PasteData(
                    appInstanceId = "spoofed-peer",
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.TEXT_TYPE.type,
                    size = 4,
                    hash = "hash",
                )

            handler.handleMessage(
                authenticatedPeer,
                WsEnvelope(
                    type = WsMessageType.PASTE_PUSH,
                    payload = getJsonUtils().JSON.encodeToString(body).encodeToByteArray(),
                ),
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboard(any()) }
            assertEquals(authenticatedPeer, receivedPaste.captured.appInstanceId)
            assertTrue(receivedPaste.captured.remote)
        }
}
