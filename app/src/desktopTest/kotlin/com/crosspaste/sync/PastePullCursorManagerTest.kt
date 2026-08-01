package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PastePullCursorManagerTest {

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
            val manager = PastePullCursorManager(pasteDao, mockk(relaxed = true))

            manager.init()

            assertEquals(100L, manager.getMaxCreateTime("stored-only"))
            assertEquals(300L, manager.getMaxCreateTime("cursor-only"))
            assertEquals(250L, manager.getMaxCreateTime("both"))
        }

    @Test
    fun `discard cursor is persisted and updates memory for an existing device`() =
        runTest {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val syncRuntimeInfoDao = mockk<SyncRuntimeInfoDao>()
            coEvery { syncRuntimeInfoDao.getSyncRuntimeInfo("remote-device") } returns mockk()
            val manager = PastePullCursorManager(pasteDao, syncRuntimeInfoDao)

            val persisted = manager.persistDiscardedMaxCreateTime("remote-device", 200L)

            assertTrue(persisted)
            assertEquals(200L, manager.getMaxCreateTime("remote-device"))
            coVerify(exactly = 1) {
                pasteDao.upsertPastePullCursorMaxCreateTime("remote-device", 200L)
            }
        }

    @Test
    fun `device removal restores stored paste cursor and does not recreate discarded cursor`() =
        runTest {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.getMaxCreateTimeByRemoteAppInstanceId() } returns
                mapOf("remote-device" to 100L)
            val syncRuntimeInfoDao = mockk<SyncRuntimeInfoDao>()
            coEvery { syncRuntimeInfoDao.getSyncRuntimeInfo("remote-device") } returns null
            val manager = PastePullCursorManager(pasteDao, syncRuntimeInfoDao)

            manager.updateMaxCreateTime("remote-device", 150L)
            manager.removeDevice("remote-device")
            val persisted = manager.persistDiscardedMaxCreateTime("remote-device", 200L)

            assertFalse(persisted)
            assertEquals(100L, manager.getMaxCreateTime("remote-device"))
            coVerify(exactly = 1) { pasteDao.deletePastePullCursor("remote-device") }
            coVerify(exactly = 0) { pasteDao.upsertPastePullCursorMaxCreateTime(any(), any()) }
        }
}
