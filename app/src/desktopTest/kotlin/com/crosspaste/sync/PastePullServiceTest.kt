package com.crosspaste.sync

import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.paste.PasteboardService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PastePullServiceTest {

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
}
