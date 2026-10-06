package com.crosspaste.sync

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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            coVerify(exactly = 0) { cursorManager.persistDiscardedMaxCreateTime(any(), any()) }
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
}
