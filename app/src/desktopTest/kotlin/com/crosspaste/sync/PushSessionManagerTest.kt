package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.presist.FilesIndex
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
    fun reservation_tracksSlotReservationAndRelease() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.markDeletePasteData(any()) } returns Result.success(Unit)
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(any()) } returns Result.success(Unit)
            val (mgr) =
                newManager(
                    maxActive = 1,
                    sessionTtl = 10.milliseconds,
                    pasteDao = pasteDao,
                    pasteboardService = pasteboardService,
                )

            val preparation = assertNotNull(mgr.tryReserve())
            assertNull(mgr.tryReserve())
            preparation.release()
            assertNotNull(mgr.tryReserve()).release()

            val session = mgr.create(1L, "mobile", fakeFilesIndex(1))!!
            assertNull(mgr.tryReserve())
            assertNull(mgr.create(2L, "mobile", fakeFilesIndex(1)))

            // Finalize frees the slot.
            session.markReceived(0)
            assertEquals(PushCompletionResult.Complete, mgr.finalizeIfComplete(session))
            assertNotNull(mgr.tryReserve()).release()

            // Sweep-expiry of an incomplete session frees the slot too.
            mgr.create(3L, "mobile", fakeFilesIndex(2))!!
            assertNull(mgr.tryReserve())
            delay(50.milliseconds)
            mgr.sweepExpired()
            assertNotNull(mgr.create(4L, "mobile", fakeFilesIndex(1)))
            mgr.close()
        }

    @Test
    fun reservation_neverExceedsMaxActiveBeforePreparation() =
        runBlocking {
            val maxActive = 4
            val (mgr) = newManager(maxActive = maxActive)
            val reservations =
                (0 until 64)
                    .map {
                        async(Dispatchers.Default) {
                            mgr.tryReserve()
                        }
                    }.awaitAll()
                    .filterNotNull()
            assertEquals(maxActive, reservations.size)
            assertEquals(0, mgr.activeCount(), "capacity must be held before preparation creates sessions")
            assertNull(mgr.tryReserve())

            reservations.forEachIndexed { index, reservation ->
                assertNotNull(
                    mgr.create(
                        reservation = reservation,
                        pasteId = index.toLong(),
                        fromAppInstanceId = "mobile",
                        filesIndex = fakeFilesIndex(1),
                    ),
                )
            }
            assertEquals(maxActive, mgr.activeCount())
            mgr.close()
        }

    @Test
    fun create_rejectsDuplicatePasteIdWithoutLeakingCapacity() {
        val (mgr) = newManager(maxActive = 2)
        val original = assertNotNull(mgr.create(1L, "mobile", fakeFilesIndex(1)))

        assertNull(mgr.create(1L, "mobile", fakeFilesIndex(1)))
        assertSame(original, mgr.peek(1L))
        assertNotNull(mgr.create(2L, "mobile", fakeFilesIndex(1)))
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
    fun finalizeIfComplete_waitsForPasteboardWriteBeforeRemovingSession() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(any()) } returns Result.success(Unit)
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(1L, "mobile", fakeFilesIndex(2))!!
            session.markReceived(0)
            session.markReceived(1)
            assertEquals(1, mgr.activeCount())

            assertEquals(PushCompletionResult.Complete, mgr.finalizeIfComplete(session))
            assertEquals(0, mgr.activeCount())
            assertNull(mgr.peek(1L))

            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(1L) }
            mgr.close()
        }

    @Test
    fun finalizeIfComplete_runsOnlyAfterLastChunk() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(any()) } returns Result.success(Unit)
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(5L, "mobile", fakeFilesIndex(2))!!

            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(0))
            assertEquals(
                PushCompletionResult.Incomplete(listOf(1)),
                mgr.finalizeIfComplete(session),
            )
            assertEquals(1, mgr.activeCount())

            assertEquals(PushSession.MarkResult.Accepted, session.markReceived(1))
            assertEquals(PushCompletionResult.Complete, mgr.finalizeIfComplete(session))
            assertEquals(0, mgr.activeCount())

            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(5L) }
            mgr.close()
        }

    @Test
    fun complete_reportsIncompleteAndRejectsAbsentSession() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>(relaxed = true)
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(9L, "mobile", fakeFilesIndex(3))!!
            session.markReceived(0)

            assertEquals(
                PushCompletionResult.Incomplete(listOf(1, 2)),
                mgr.complete(9L, session.token, "mobile"),
            )
            assertEquals(
                PushCompletionResult.NotFound,
                mgr.complete(404L, "missing-token", "mobile"),
            )
            assertEquals(1, mgr.activeCount())
            coVerify(exactly = 0) { pasteboardService.tryWriteRemotePasteboardWithFile(any()) }
            mgr.close()
        }

    @Test
    fun finalizeIfComplete_retainsSessionAfterFailureAndAllowsRetry() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>()
            val failure = IllegalStateException("persist failed")
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(11L) } returnsMany
                listOf(Result.failure(failure), Result.success(Unit))
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(11L, "mobile", fakeFilesIndex(1))!!
            session.markReceived(0)

            val first = mgr.finalizeIfComplete(session)
            assertTrue(first is PushCompletionResult.Failed)
            assertSame(failure, first.cause)
            assertSame(session, mgr.peek(11L))
            assertEquals(1, mgr.activeCount())

            assertEquals(PushCompletionResult.Complete, mgr.finalizeIfComplete(session))
            assertNull(mgr.peek(11L))
            assertEquals(0, mgr.activeCount())
            coVerify(exactly = 2) { pasteboardService.tryWriteRemotePasteboardWithFile(11L) }
            mgr.close()
        }

    @Test
    fun concurrentFinalization_runsDurableTransitionOnce() =
        runBlocking {
            val pasteboardService = mockk<PasteboardService>()
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(12L) } coAnswers {
                delay(20.milliseconds)
                Result.success(Unit)
            }
            val (mgr) = newManager(pasteboardService = pasteboardService)
            val session = mgr.create(12L, "mobile", fakeFilesIndex(1))!!
            session.markReceived(0)

            val results =
                listOf(
                    async { mgr.finalizeIfComplete(session) },
                    async { mgr.finalizeIfComplete(session) },
                ).awaitAll()

            assertEquals(listOf(PushCompletionResult.Complete, PushCompletionResult.Complete), results)
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(12L) }
            assertEquals(0, mgr.activeCount())
            mgr.close()
        }

    @Test
    fun complete_acceptsOnlyDurableLoadedPasteFromAuthenticatedSender() =
        runBlocking {
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.getNoDeletePasteData(21L) } returns storedPaste(21L, "mobile", PasteState.LOADED)
            coEvery { pasteDao.getNoDeletePasteData(22L) } returns storedPaste(22L, "mobile", PasteState.LOADING)
            coEvery { pasteDao.getNoDeletePasteData(23L) } returns storedPaste(23L, "other", PasteState.LOADED)
            val (mgr) = newManager(pasteDao = pasteDao)

            assertEquals(PushCompletionResult.Complete, mgr.complete(21L, "expired-token", "mobile"))
            assertEquals(PushCompletionResult.NotFound, mgr.complete(22L, "expired-token", "mobile"))
            assertEquals(PushCompletionResult.NotFound, mgr.complete(23L, "expired-token", "mobile"))
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

            assertEquals(0, mgr.activeCount())
            val finalizedId = slot<Long>()
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(capture(finalizedId)) }
            assertEquals(77L, finalizedId.captured)
            coVerify(exactly = 0) { pasteDao.markDeletePasteData(any()) }
            mgr.close()
        }

    @Test
    fun sweepExpired_discardsCompleteSessionWhenFinalizationStillFails() =
        runBlocking {
            val failure = IllegalStateException("permanent finalize failure")
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.markDeletePasteData(78L) } returns Result.success(Unit)
            val pasteboardService = mockk<PasteboardService>()
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(78L) } returns Result.failure(failure)
            val (mgr) =
                newManager(
                    sessionTtl = 10.milliseconds,
                    pasteDao = pasteDao,
                    pasteboardService = pasteboardService,
                )
            val session = mgr.create(78L, "mobile", fakeFilesIndex(1))!!
            session.markReceived(0)

            delay(50.milliseconds)
            mgr.sweepExpired()

            assertNull(mgr.peek(78L))
            assertEquals(0, mgr.activeCount())
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(78L) }
            coVerify(exactly = 1) { pasteDao.markDeletePasteData(78L) }
            mgr.close()
        }

    @Test
    fun sweepExpired_doesNotDeleteRowCommittedByConcurrentFinalizeRetry() =
        runBlocking {
            // Sweep's finalize attempt fails while a request retry is already
            // waiting on the terminal lock. The retry wins the lock next (the
            // mutex is fair), commits LOADED, and sweep's discard must then be
            // a no-op instead of deleting the durable row.
            val sweepFinalizeGate = CompletableDeferred<Unit>()
            val finalizeCalls =
                java.util.concurrent.atomic
                    .AtomicInteger(0)
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.markDeletePasteData(79L) } returns Result.success(Unit)
            val pasteboardService = mockk<PasteboardService>()
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(79L) } coAnswers {
                if (finalizeCalls.incrementAndGet() == 1) {
                    sweepFinalizeGate.await()
                    Result.failure(IllegalStateException("transient finalize failure"))
                } else {
                    Result.success(Unit)
                }
            }
            val (mgr) =
                newManager(
                    maxActive = 1,
                    sessionTtl = 10.milliseconds,
                    pasteDao = pasteDao,
                    pasteboardService = pasteboardService,
                )
            val session = mgr.create(79L, "mobile", fakeFilesIndex(1))!!
            session.markReceived(0)
            delay(50.milliseconds)

            val sweep = launch { mgr.sweepExpired() }
            yield() // sweep now holds the terminal lock inside its finalize attempt
            val retry = async { mgr.complete(79L, session.token, "mobile") }
            yield() // retry passed the session lookup and is queued on the lock
            sweepFinalizeGate.complete(Unit)
            sweep.join()

            assertEquals(PushCompletionResult.Complete, retry.await())
            assertNull(mgr.peek(79L))
            assertEquals(0, mgr.activeCount())
            coVerify(exactly = 2) { pasteboardService.tryWriteRemotePasteboardWithFile(79L) }
            coVerify(exactly = 0) { pasteDao.markDeletePasteData(any()) }
            assertNotNull(mgr.create(80L, "mobile", fakeFilesIndex(1)), "slot must be released exactly once")
            mgr.close()
        }

    @Test
    fun sweepExpired_discardOwnsTerminalStateBeforeWaitingRetry() =
        runBlocking {
            // Sweep's finalize fails and its discard takes the terminal lock
            // first. A request that resolved the session before the discard
            // finished must observe NotFound, not retry finalization and
            // report a success the deletion would immediately undo.
            val discardGate = CompletableDeferred<Unit>()
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.markDeletePasteData(81L) } coAnswers {
                discardGate.await()
                Result.success(Unit)
            }
            val pasteboardService = mockk<PasteboardService>()
            coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(81L) } returns
                Result.failure(IllegalStateException("permanent finalize failure"))
            val (mgr) =
                newManager(
                    maxActive = 1,
                    sessionTtl = 10.milliseconds,
                    pasteDao = pasteDao,
                    pasteboardService = pasteboardService,
                )
            val session = mgr.create(81L, "mobile", fakeFilesIndex(1))!!
            session.markReceived(0)
            delay(50.milliseconds)

            val sweep = launch { mgr.sweepExpired() }
            yield() // finalize failed; discard holds the terminal lock and is marking the row deleted
            assertNotNull(mgr.peek(81L), "session stays visible until the row is marked deleted")
            val retry = async { mgr.complete(81L, session.token, "mobile") }
            yield() // retry resolved the live session and is queued on the terminal lock
            discardGate.complete(Unit)
            sweep.join()

            assertEquals(PushCompletionResult.NotFound, retry.await())
            assertNull(mgr.peek(81L))
            assertEquals(0, mgr.activeCount())
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(81L) }
            coVerify(exactly = 1) { pasteDao.markDeletePasteData(81L) }
            assertNotNull(mgr.create(82L, "mobile", fakeFilesIndex(1)), "slot must be released exactly once")
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

    private fun storedPaste(
        id: Long,
        appInstanceId: String,
        state: Int,
    ): PasteData =
        PasteData(
            id = id,
            appInstanceId = appInstanceId,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            size = 4,
            hash = "hash-$id",
            pasteState = state,
        )
}
