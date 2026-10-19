package com.crosspaste.paste

import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.headless.HeadlessPasteboardService
import com.crosspaste.sync.PullTestStore
import com.crosspaste.sync.pullTestPaste
import com.crosspaste.task.DesktopTaskBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteCatchupReleaseTest {
    @Test fun `batch history never relays even when local clipboard writes are enabled`() =
        runTest {
            for (writeClipboard in listOf(false, true)) {
                val s = PullTestStore()
                val cursor = s.cursor()
                val board = HeadlessPasteboardService(s.config, s.release(cursor))
                assertTrue(
                    board
                        .tryWriteRemotePasteboardList(
                            listOf(pullTestPaste(10), pullTestPaste(11)),
                            writeClipboard,
                        ).isSuccess,
                )
                verify(exactly = 0) { s.tasks.addRelaySyncTask(any(), any(), any()) }
                assertEquals(writeClipboard, board.remotePasteboardChannel.tryReceive().isSuccess)
                assertEquals(11L, cursor.getRemoteId("peer"))
            }
        }

    @Test fun `file finalization honors history mode and live files still relay`() =
        runTest {
            for (history in listOf(false, true)) {
                val s = PullTestStore()
                val c = s.cursor()
                val localId = s.dao.createPasteData(pullTestPaste(100).copy(remote = true), PasteState.LOADING)
                c.reserve("peer", listOf(100L))
                c.bindFile("peer", 100, localId)
                val result = s.release(c).releaseRemotePasteDataWithFile(localId, historyPull = history) { }
                assertTrue(result.isSuccess)
                assertEquals(100L, c.getRemoteId("peer"))
                verify(exactly = if (history) 0 else 1) { s.tasks.addRelaySyncTask(localId, "peer", emptySet()) }
            }
        }

    @Test fun `history mode survives task serialization and old tasks keep live defaults`() {
        val s = PullTestStore()
        val taskDao = mockk<TaskDao>(relaxed = true)
        var saved: PullExtraInfo? = null
        every { taskDao.createTaskBlock(any(), any(), any()) } answers {
            saved = thirdArg<com.crosspaste.db.task.PasteTaskExtraInfo>() as PullExtraInfo
            1L
        }
        DesktopTaskBuilder(mockk(), mockk(), taskDao).addPullFileTask(7, 100, emptySet(), false, true)
        val restored =
            s.json.decodeFromString(
                PullExtraInfo.serializer(),
                s.json.encodeToString(PullExtraInfo.serializer(), saved!!),
            )
        assertTrue(restored.historyPull)
        assertFalse(restored.writePasteboard)
        assertEquals(100L, restored.id)
        val old = s.json.decodeFromString(PullExtraInfo.serializer(), "{\"id\":100}")
        assertFalse(old.historyPull)
        assertTrue(old.writePasteboard)
    }
}
