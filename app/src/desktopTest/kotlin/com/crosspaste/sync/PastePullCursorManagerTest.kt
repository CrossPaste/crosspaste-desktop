package com.crosspaste.sync

import com.crosspaste.app.AppFileType
import com.crosspaste.db.DesktopDriverFactory
import com.crosspaste.paste.PasteState
import com.crosspaste.path.UserDataPathProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PastePullCursorManagerTest {
    @Test fun `time cursors never become ID cursors on migration`() =
        runTest {
            val s = PullTestStore()
            s.dao.upsertPastePullCursorMaxCreateTime("peer", 5000000000000L)
            val c = s.cursor()
            c.init()
            assertNull(c.getRemoteId("peer"))
        }

    @Test fun `completed receipt survives restart and local history deletion`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.reserve("peer", listOf(20L))
            c.settle("peer", 20L)
            val restored = s.cursor()
            restored.init()
            assertEquals(20L, restored.getRemoteId("peer"))
            assertEquals(emptyList(), restored.selectUnreceived("peer", listOf(pullTestPaste(20))))
        }

    @Test fun `newer success cannot hide a failed earlier receive across restart`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.settle("peer", 10)
            c.reserve("peer", listOf(11L, 12L))
            c.settle("peer", 12)
            val restored = s.cursor()
            restored.init()
            assertEquals(10L, restored.getQueryAfterId("peer"))
            assertEquals(
                listOf(11L),
                restored.selectUnreceived("peer", listOf(pullTestPaste(12), pullTestPaste(11))).map { it.id },
            )
            restored.settle("peer", 11)
            assertEquals(12L, restored.getQueryAfterId("peer"))
        }

    @Test fun `pending file is not downloaded twice and failed file can retry`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.settle("peer", 10)
            c.reserve("peer", listOf(11L, 12L))
            val localId = s.dao.createPasteData(pullTestPaste(11), PasteState.LOADING)
            c.bindFile("peer", 11, localId)
            c.settle("peer", 12)
            assertEquals(emptyList(), c.selectUnreceived("peer", listOf(pullTestPaste(11))))
            s.dao.markDeleteFailedReceive(localId)
            assertEquals(listOf(11L), c.selectUnreceived("peer", listOf(pullTestPaste(11))).map { it.id })
        }

    @Test fun `file finalize advances with original ID instead of local ID or time`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.reserve("peer", listOf(100L))
            val localId = s.dao.createPasteData(pullTestPaste(100), PasteState.LOADING)
            c.bindFile("peer", 100, localId)
            assertNull(c.getRemoteId("peer"))
            c.settleFile("peer", localId)
            assertEquals(100L, c.getRemoteId("peer"))
            assertEquals(emptyMap(), s.dao.getPendingPasteReceives("peer"))
        }

    @Test fun `removed peer cannot recreate receipt or baseline`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.settle("peer", 10)
            c.reserve("peer", listOf(11L))
            s.peerExists = false
            c.removeDevice("peer")
            c.reserve("peer", listOf(12L))
            assertFalse(c.settle("peer", 12))
            assertNull(c.getRemoteId("peer"))
            assertEquals(emptyMap(), s.dao.getPendingPasteReceives("peer"))
        }

    @Test fun `migration creates separate receipt storage and indexed ID query`() =
        runTest {
            val s = PullTestStore()
            val driver = s.driverFactory.sqlDriver!!
            driver.execute(null, "DROP TABLE PasteRemoteCursorEntity", 0)
            driver.execute(null, "DROP TABLE PastePendingReceiveEntity", 0)
            driver.execute(null, "DROP INDEX IdxPasteDataInstanceStateId", 0)
            s.dao.upsertPastePullCursorMaxCreateTime("peer", 5000000000000L)
            com.crosspaste.Database.Schema
                .migrate(driver, 7, 8)
            val c = s.cursor()
            c.init()
            assertNull(c.getRemoteId("peer"))
            c.settle("peer", 100)
            assertEquals(mapOf("peer" to 100L), s.dao.getRemotePasteCursors())
            val plan =
                driver
                    .executeQuery(
                        null,
                        "EXPLAIN QUERY PLAN SELECT * FROM PasteDataEntity WHERE appInstanceId = 'peer' AND pasteState = 1 AND id > 50 ORDER BY id DESC LIMIT 50",
                        { cursor ->
                            val lines = mutableListOf<String>()
                            while (cursor.next().value) lines.add(cursor.getString(3)!!)
                            app.cash.sqldelight.db.QueryResult
                                .Value(lines.joinToString(" "))
                        },
                        0,
                    ).value
            kotlin.test.assertTrue(plan.contains("IdxPasteDataInstanceStateId"), plan)
            kotlin.test.assertFalse(plan.contains("TEMP B-TREE"), plan)
        }

    @Test fun `user deletion of downloading paste is terminal including before first baseline`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.reserve("peer", listOf(100L))
            val localId = s.dao.createPasteData(pullTestPaste(100), PasteState.LOADING)
            c.bindFile("peer", 100, localId)
            s.dao.markDeletePasteData(localId).getOrThrow()
            assertEquals(100L, c.getQueryAfterId("peer"))
            assertEquals(emptyList(), c.selectUnreceived("peer", listOf(pullTestPaste(100))))
        }

    @Test fun `deletion before pending binding does not resurrect a receive`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.reserve("peer", listOf(100L))
            val localId = s.dao.createPasteData(pullTestPaste(100), PasteState.LOADING)
            s.dao.markDeletePasteData(localId).getOrThrow()
            c.bindFile("peer", 100, localId)
            assertEquals(100L, c.getQueryAfterId("peer"))
            assertEquals(emptyMap(), s.dao.getPendingPasteReceives("peer"))
        }

    @Test fun `clear history terminates pending downloads`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.reserve("peer", listOf(100L))
            val localId = s.dao.createPasteData(pullTestPaste(100), PasteState.LOADING)
            c.bindFile("peer", 100, localId)
            s.dao.markAllDeleteExceptTagged().getOrThrow()
            assertEquals(100L, c.getQueryAfterId("peer"))
        }

    @Test fun `late reservation cannot recreate a user deleted receipt from stale memory`() =
        runTest {
            val s = PullTestStore()
            val c = s.cursor()
            c.settle("peer", 10)
            c.reserve("peer", listOf(11L))
            val localId = s.dao.createPasteData(pullTestPaste(11), PasteState.LOADING)
            c.bindFile("peer", 11, localId)
            s.dao.markDeletePasteData(localId).getOrThrow()
            assertEquals(10L, c.getRemoteId("peer"))
            c.reserve("peer", listOf(11L))
            kotlin.test.assertTrue(c.shouldSkipHistory(pullTestPaste(11)))
            assertEquals(emptyMap(), s.dao.getPendingPasteReceives("peer"))
        }

    @Test fun `deleting an active receive concurrently with history selection never makes it retryable`() =
        runTest {
            // Use the production connection pool: the in-memory test driver shares one
            // JDBC connection and does not provide isolation across test threads.
            val directory =
                java.nio.file.Files
                    .createTempDirectory("catchup-delete-test")
            val paths = mockk<UserDataPathProvider>()
            every { paths.resolve("crosspaste.db", AppFileType.DATA) } returns
                directory.resolve("crosspaste.db").toOkioPath()
            val factory = DesktopDriverFactory(paths)
            try {
                val s = PullTestStore(factory)
                val c = s.cursor()
                c.settle("peer", 10)
                repeat(50) { index ->
                    val remoteId = 11L + index
                    c.reserve("peer", listOf(remoteId))
                    val localId = s.dao.createPasteData(pullTestPaste(remoteId), PasteState.LOADING)
                    c.bindFile("peer", remoteId, localId)
                    kotlinx.coroutines.coroutineScope {
                        val start = kotlinx.coroutines.CompletableDeferred<Unit>()
                        val selection =
                            async(kotlinx.coroutines.Dispatchers.Default) {
                                start.await()
                                c.selectUnreceived("peer", listOf(pullTestPaste(remoteId)))
                            }
                        val deletion =
                            async(kotlinx.coroutines.Dispatchers.Default) {
                                start.await()
                                s.dao.markDeletePasteData(localId).getOrThrow()
                            }
                        start.complete(Unit)
                        assertEquals(emptyList(), selection.await())
                        deletion.await()
                    }
                    assertEquals(emptyList(), c.selectUnreceived("peer", listOf(pullTestPaste(remoteId))))
                }
            } finally {
                factory.closeDriver()
                directory.toFile().deleteRecursively()
            }
        }
}
