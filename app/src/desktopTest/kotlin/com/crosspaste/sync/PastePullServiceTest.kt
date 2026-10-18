package com.crosspaste.sync

import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PastePullServiceTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun newService(pastePullCursorManager: PastePullCursorManager): PastePullService =
        PastePullService(
            pastePullCursorManager = pastePullCursorManager,
            pasteboardService = mockk<PasteboardService>(relaxed = true),
            pullClientApi = mockk<PullClientApi>(relaxed = true),
            syncManager = mockk<SyncManager>(relaxed = true),
        )

    @Test
    fun `init delegates to cursor manager`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)

            val service = newService(cursorManager)
            service.init()

            coVerify(exactly = 1) { cursorManager.init() }
        }

    @Test
    fun `in-memory cursor update does not mark an unpersisted paste as durable`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)
            coEvery { cursorManager.getMaxCreateTime("remote-device") } returns 300L

            val service = newService(cursorManager)
            service.updateMaxCreateTime("remote-device", 300L)

            assertEquals(300L, service.getMaxCreateTime("remote-device"))
            coVerify(exactly = 1) { cursorManager.updateMaxCreateTime("remote-device", 300L) }
            coVerify(exactly = 0) { cursorManager.persistMaxCreateTime(any(), any()) }
        }

    @Test
    fun `pulled pastes are bound to the device they were pulled from`() =
        runTest {
            val handler = mockk<SyncHandler>(relaxed = true)
            coEvery { handler.getConnectHostAddress() } returns "192.168.1.100"
            val syncManager = mockk<SyncManager>(relaxed = true)
            coEvery { syncManager.getSyncHandler("remote-device") } returns handler
            val pullClientApi = mockk<PullClientApi>(relaxed = true)
            val item = createTextPasteItem(text = "pulled")
            // A peer claiming someone else's identity must not pass as that device's paste
            val claimed =
                PasteData(
                    appInstanceId = "local-device",
                    pasteAppearItem = item,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.TEXT_TYPE.type,
                    source = null,
                    size = item.size,
                    hash = item.hash,
                )
            coEvery { pullClientApi.pullPasteBatch("remote-device", any(), any(), any()) } returns
                SuccessResult(listOf(claimed))
            val service =
                PastePullService(
                    pastePullCursorManager = mockk(relaxed = true),
                    pasteboardService = mockk<PasteboardService>(relaxed = true),
                    pullClientApi = pullClientApi,
                    syncManager = syncManager,
                )

            val pulled = service.pullBatch("remote-device").single()

            assertEquals("remote-device", pulled.appInstanceId)
            assertTrue(pulled.remote)
        }

    private fun remotePaste(createTime: Long): PasteData {
        val item = createTextPasteItem(text = "pulled-$createTime")
        return PasteData(
            appInstanceId = "remote-device",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            source = null,
            size = item.size,
            hash = item.hash,
            createTime = createTime,
        )
    }

    private fun connectedSyncManager(
        connectState: Int = SyncState.CONNECTED,
        versionRelation: VersionRelation = VersionRelation.EQUAL_TO,
    ): SyncManager {
        val handler = mockk<SyncHandler>(relaxed = true)
        coEvery { handler.getConnectHostAddress() } returns "192.168.1.100"
        every { handler.currentSyncRuntimeInfo } returns
            SyncTestFixtures.createSyncRuntimeInfo(appInstanceId = "remote-device", connectState = connectState)
        every { handler.currentVersionRelation } returns versionRelation
        val syncManager = mockk<SyncManager>(relaxed = true)
        every { syncManager.getSyncHandler("remote-device") } returns handler
        return syncManager
    }

    @Test
    fun `pullDevice writes the batch oldest first without touching clipboard and advances cursor monotonically`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val previousCursor = 5000000000000L
            every { cursorManager.getMaxCreateTime("remote-device") } returns previousCursor
            val pullClientApi = mockk<PullClientApi>()
            // The peer answers newest first
            coEvery { pullClientApi.pullPasteBatch("remote-device", previousCursor, 50L, any()) } returns
                SuccessResult(listOf(remotePaste(300L), remotePaste(200L)))
            val pasteboardService = mockk<PasteboardService>()
            var written: List<PasteData> = emptyList()
            var wroteToPasteboard = true
            coEvery { pasteboardService.tryWriteRemotePasteboardList(any(), any()) } answers {
                written = firstArg()
                wroteToPasteboard = secondArg()
                Result.success(null)
            }
            val service = PastePullService(cursorManager, pasteboardService, pullClientApi, connectedSyncManager())

            service.pullDevice("remote-device")

            // Restamped from the local clock, oldest first, so history keeps the peer's order
            assertEquals(listOf(remotePaste(200L).hash, remotePaste(300L).hash), written.map { it.hash })
            assertTrue(written[0].createTime > previousCursor)
            assertTrue(written[0].createTime < written[1].createTime)
            assertFalse(wroteToPasteboard)
            coVerify(exactly = 1) { cursorManager.updateMaxCreateTime("remote-device", written[1].createTime) }
        }

    @Test
    fun `a failed write leaves the cursor for a later retry`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)
            every { cursorManager.getMaxCreateTime("remote-device") } returns 100L
            val pullClientApi = mockk<PullClientApi>()
            coEvery { pullClientApi.pullPasteBatch(any(), any(), any(), any()) } returns
                SuccessResult(listOf(remotePaste(200L)))
            val pasteboardService = mockk<PasteboardService>()
            coEvery { pasteboardService.tryWriteRemotePasteboardList(any(), any()) } returns
                Result.failure(IllegalStateException("database write failed"))
            val service = PastePullService(cursorManager, pasteboardService, pullClientApi, connectedSyncManager())

            service.pullDevice("remote-device")

            coVerify(exactly = 0) { cursorManager.updateMaxCreateTime(any(), any()) }
        }

    @Test
    fun `a failed fetch writes nothing and keeps the cursor`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)
            every { cursorManager.getMaxCreateTime("remote-device") } returns 100L
            val pullClientApi = mockk<PullClientApi>()
            coEvery { pullClientApi.pullPasteBatch(any(), any(), any(), any()) } returns ConnectionRefused
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            val service = PastePullService(cursorManager, pasteboardService, pullClientApi, connectedSyncManager())

            service.pullDevice("remote-device")

            coVerify(exactly = 0) { pasteboardService.tryWriteRemotePasteboardList(any(), any()) }
            coVerify(exactly = 0) { cursorManager.updateMaxCreateTime(any(), any()) }
        }

    @Test
    fun `pullDevice does nothing without a cursor`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)
            every { cursorManager.getMaxCreateTime("remote-device") } returns null
            val pullClientApi = mockk<PullClientApi>(relaxed = true)
            val service =
                PastePullService(cursorManager, mockk(relaxed = true), pullClientApi, connectedSyncManager())

            service.pullDevice("remote-device")

            coVerify(exactly = 0) { pullClientApi.pullPasteBatch(any(), any(), any(), any()) }
        }

    @Test
    fun `pullDevice skips peers that are not connected or not compatible`() =
        runTest {
            val cursorManager = mockk<PastePullCursorManager>(relaxed = true)
            every { cursorManager.getMaxCreateTime("remote-device") } returns 100L
            val pullClientApi = mockk<PullClientApi>(relaxed = true)

            PastePullService(
                cursorManager,
                mockk(relaxed = true),
                pullClientApi,
                connectedSyncManager(connectState = SyncState.DISCONNECTED),
            ).pullDevice("remote-device")
            PastePullService(
                cursorManager,
                mockk(relaxed = true),
                pullClientApi,
                connectedSyncManager(versionRelation = VersionRelation.LOWER_THAN),
            ).pullDevice("remote-device")

            coVerify(exactly = 0) { pullClientApi.pullPasteBatch(any(), any(), any(), any()) }
        }
}
