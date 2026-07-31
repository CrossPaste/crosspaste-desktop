package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.paste.PasteboardService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PastePullServiceTest {

    private fun newService(pasteDao: PasteDao): PastePullService =
        PastePullService(
            pasteDao = pasteDao,
            pasteboardService = mockk<PasteboardService>(relaxed = true),
            pullClientApi = mockk<PullClientApi>(relaxed = true),
            syncManager = mockk<SyncManager>(relaxed = true),
        )

    @Test
    fun `init restores newest cursor from stored pastes and durable pull cursor`() =
        runTest {
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.getMaxCreateTimeByRemoteAppInstanceId() } returns
                mapOf(
                    "stored-only" to 100L,
                    "both" to 200L,
                )
            coEvery { pasteDao.getPastePullCursorMaxCreateTimes() } returns
                mapOf(
                    "cursor-only" to 300L,
                    "both" to 250L,
                )

            val service = newService(pasteDao)
            service.init()

            assertEquals(100L, service.getMaxCreateTime("stored-only"))
            assertEquals(300L, service.getMaxCreateTime("cursor-only"))
            assertEquals(250L, service.getMaxCreateTime("both"))
        }

    @Test
    fun `in-memory cursor update does not mark an unpersisted paste as durable`() =
        runTest {
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.getMaxCreateTimeByRemoteAppInstanceId() } returns emptyMap()
            coEvery { pasteDao.getPastePullCursorMaxCreateTimes() } returns
                mapOf("remote-device" to 200L)

            val service = newService(pasteDao)
            service.init()
            service.updateMaxCreateTime("remote-device", 300L)

            assertEquals(300L, service.getMaxCreateTime("remote-device"))
            coVerify(exactly = 0) { pasteDao.upsertPastePullCursorMaxCreateTime(any(), any()) }
        }
}
