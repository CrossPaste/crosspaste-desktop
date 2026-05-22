package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteboardService
import com.crosspaste.presist.FilesIndex
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PushSessionManagerTest {

    private fun fakeFilesIndex(chunkCount: Int): FilesIndex =
        mockk<FilesIndex>().also { every { it.getChunkCount() } returns chunkCount }

    private fun newManager(
        maxActive: Int = 16,
        sessionTtl: Duration = 90_000L.milliseconds,
        sweepInterval: Duration = 60_000L.milliseconds,
        scope: CoroutineScope = CoroutineScope(Job()),
        pasteDao: PasteDao = mockk(relaxed = true),
        pasteboardService: PasteboardService = mockk(relaxed = true),
    ): Triple<PushSessionManager, PasteDao, PasteboardService> {
        val mgr =
            PushSessionManager(
                pasteDao = pasteDao,
                pasteboardService = pasteboardService,
                maxActive = maxActive,
                sessionTtl = sessionTtl,
                sweepInterval = sweepInterval,
                scope = scope,
            )
        return Triple(mgr, pasteDao, pasteboardService)
    }

    @Test
    fun create_returnsSessionWithUniqueTokenAndFilesIndex() {
        val (mgr) = newManager()
        val indexA = fakeFilesIndex(3)
        val indexB = fakeFilesIndex(5)
        val a = mgr.create(pasteId = 1L, fromAppInstanceId = "mobile-a", filesIndex = indexA)
        val b = mgr.create(pasteId = 2L, fromAppInstanceId = "mobile-b", filesIndex = indexB)
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(1L, a.pasteId)
        assertEquals(3, a.chunkCount)
        assertSame(indexA, a.filesIndex)
        assertEquals("mobile-a", a.fromAppInstanceId)
        assertEquals(2, mgr.activeCount())
        assertNotEquals(a.token, b.token)
        mgr.close()
    }

    @Test
    fun create_rejectsWhenChunkCountInvalid() {
        val (mgr) = newManager()
        assertNull(mgr.create(pasteId = 1L, fromAppInstanceId = "mobile", filesIndex = fakeFilesIndex(0)))
        assertNull(mgr.create(pasteId = 2L, fromAppInstanceId = "mobile", filesIndex = fakeFilesIndex(-1)))
        assertEquals(0, mgr.activeCount())
        mgr.close()
    }

    @Test
    fun create_rejectsWhenMaxActiveReached() {
        val (mgr) = newManager(maxActive = 2)
        assertNotNull(mgr.create(1L, "mobile", fakeFilesIndex(1)))
        assertNotNull(mgr.create(2L, "mobile", fakeFilesIndex(1)))
        assertNull(mgr.create(3L, "mobile", fakeFilesIndex(1)))
        assertEquals(2, mgr.activeCount())
        mgr.close()
    }

    @Test
    fun get_requiresMatchingTokenAndAppInstance() {
        val (mgr) = newManager()
        val session = mgr.create(1L, "mobile-a", fakeFilesIndex(2))!!
        assertNotNull(mgr.get(1L, session.token, "mobile-a"))
        assertNull(mgr.get(1L, "wrong-token", "mobile-a"))
        assertNull(mgr.get(1L, session.token, "mobile-b"))
        assertNull(mgr.get(2L, session.token, "mobile-a"))
        mgr.close()
    }

    @Test
    fun markReceived_isIdempotentAndCounts() =
        runBlocking {
            val (mgr) = newManager()
            val session = mgr.create(1L, "mobile", fakeFilesIndex(3))!!
            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(0))
            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(2))
            assertEquals(PushSession.MarkResult.AlreadyReceived, session.markReceived(0))
            assertEquals(2, session.receivedCount)
            assertFalse(session.isComplete)
            assertEquals(listOf(1), session.missingChunks())

            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(1))
            assertTrue(session.isComplete)
            assertEquals(emptyList(), session.missingChunks())
            mgr.close()
        }

    @Test
    fun markReceived_outOfRange() =
        runBlocking {
            val (mgr) = newManager()
            val session = mgr.create(1L, "mobile", fakeFilesIndex(3))!!
            assertEquals(PushSession.MarkResult.OutOfRange, session.markReceived(-1))
            assertEquals(PushSession.MarkResult.OutOfRange, session.markReceived(3))
            assertEquals(0, session.receivedCount)
            mgr.close()
        }

    @Test
    fun tryFinalize_removesSessionAndQueuesPasteboardWrite() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(any()) } returns Result.success(Unit)
            val (mgr) = newManager(pasteboardService = pasteboardService)
            mgr.create(1L, "mobile", fakeFilesIndex(2))!!
            assertEquals(1, mgr.activeCount())

            assertTrue(mgr.tryFinalize(1L), "tryFinalize returns true when caller claims removal")
            assertEquals(0, mgr.activeCount())
            assertNull(mgr.peek(1L))
            assertFalse(mgr.tryFinalize(1L), "second call returns false — session already gone")

            // Finalize is launched on the manager's scope; let it run.
            delay(50.milliseconds)
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(1L) }
            mgr.close()
        }

    @Test
    fun tryFinalizeIfComplete_triggersOnLastChunk() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(any()) } returns Result.success(Unit)
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(5L, "mobile", fakeFilesIndex(2))!!

            // First chunk: not yet complete — tryFinalizeIfComplete is a no-op.
            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(0))
            assertFalse(mgr.tryFinalizeIfComplete(5L))
            assertEquals(1, mgr.activeCount())

            // Last chunk: session becomes complete — tryFinalizeIfComplete fires.
            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(1))
            assertTrue(mgr.tryFinalizeIfComplete(5L))
            assertEquals(0, mgr.activeCount())

            delay(50.milliseconds)
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(5L) }
            mgr.close()
        }

    @Test
    fun tryFinalizeIfComplete_isNoopForIncompleteOrAbsent() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(9L, "mobile", fakeFilesIndex(3))!!
            session.markReceived(0)

            assertFalse(mgr.tryFinalizeIfComplete(9L), "not complete yet")
            assertFalse(mgr.tryFinalizeIfComplete(404L), "no session for this id")
            assertEquals(1, mgr.activeCount())
            coVerify(exactly = 0) { pasteboardService.tryWriteRemotePasteboardWithFile(any()) }
            mgr.close()
        }

    @Test
    fun sweepExpired_removesStaleAndMarksDeleted() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.markDeletePasteData(any()) } returns Result.success(Unit)
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            val (mgr) =
                newManager(
                    sessionTtl = 10.milliseconds,
                    pasteDao = pasteDao,
                    pasteboardService = pasteboardService,
                )
            mgr.create(42L, "mobile", fakeFilesIndex(2))!!
            assertEquals(1, mgr.activeCount())

            delay(50.milliseconds)
            mgr.sweepExpired()

            assertEquals(0, mgr.activeCount())
            val capturedId = slot<Long>()
            coVerify(exactly = 1) { pasteDao.markDeletePasteData(capture(capturedId)) }
            assertEquals(42L, capturedId.captured)
            coVerify(exactly = 0) { pasteboardService.tryWriteRemotePasteboardWithFile(any()) }
            mgr.close()
        }

    @Test
    fun sweepExpired_finalizesOrphanCompleteSession() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(any()) } returns Result.success(Unit)
            val (mgr) =
                newManager(
                    sessionTtl = 10.milliseconds,
                    pasteDao = pasteDao,
                    pasteboardService = pasteboardService,
                )
            val session = mgr.create(77L, "mobile", fakeFilesIndex(2))!!
            // Simulate the corner case: all chunks landed AND auto-finalize was
            // somehow skipped (scope cancelled mid-flight, etc.). Session is
            // still in the map when sweep TTL expires.
            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(0))
            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(1))
            assertTrue(session.isComplete)

            delay(50.milliseconds)
            mgr.sweepExpired()
            // Finalize is fire-and-forget on the manager scope; wait for it.
            delay(50.milliseconds)

            assertEquals(0, mgr.activeCount())
            val finalizedId = slot<Long>()
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(capture(finalizedId)) }
            assertEquals(77L, finalizedId.captured)
            coVerify(exactly = 0) { pasteDao.markDeletePasteData(any()) }
            mgr.close()
        }

    @Test
    fun sweepExpired_keepsActiveSessions() =
        runBlocking {
            val (mgr) = newManager(sessionTtl = 5.seconds)
            mgr.create(1L, "mobile", fakeFilesIndex(1))!!
            mgr.sweepExpired()
            assertEquals(1, mgr.activeCount())
            mgr.close()
        }
}
