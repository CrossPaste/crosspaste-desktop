package com.crosspaste.sync

import com.crosspaste.config.TestAppConfig
import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PastePullServiceTest {
    private class Harness(
        val legacy: Boolean = false,
    ) {
        val store = PullTestStore()
        val cursor = store.cursor()
        val release = store.release(cursor)
        val api = mockk<PullClientApi>()
        val board = mockk<PasteboardService>()
        val handler = mockk<SyncHandler>()
        val sync = mockk<SyncManager>()
        var info = SyncTestFixtures.createSyncRuntimeInfo(appInstanceId = "peer", connectState = SyncState.CONNECTED)
        var version = VersionRelation.EQUAL_TO
        var rows = emptyList<PasteData>()
        val received = mutableListOf<PasteData>()
        var writes = 0
        var failId: Long? = null

        init {
            every { handler.currentSyncRuntimeInfo } answers { info }
            every { handler.currentVersionRelation } answers { version }
            coEvery { handler.getConnectHostAddress() } returns "127.0.0.1"
            every { sync.getSyncHandler("peer") } returns handler
            every { sync.getSyncHandlers() } returns mapOf("peer" to handler)
            every { board.configManager } returns store.config
            coEvery { api.pullPasteBatchById("peer", any(), any(), any()) } answers {
                if (legacy) {
                    createFailureResult(StandardErrorCode.NOT_FOUND_API, "missing")
                } else {
                    val after = secondArg<Long>()
                    wire(rows.filter { it.id > after }.sortedByDescending { it.id }.take(thirdArg<Long>().toInt()))
                }
            }
            coEvery { api.pullPasteBatch("peer", null, any(), any()) } answers {
                wire(rows.sortedByDescending { it.id }.take(thirdArg<Long>().toInt()))
            }
            coEvery { board.tryWriteRemotePasteboardList(any(), any()) } coAnswers {
                val pastes = firstArg<List<PasteData>>()
                val write = secondArg<Boolean>()
                var result: Result<Unit?> = Result.success(null)
                for (paste in pastes) {
                    if (paste.id == failId) {
                        result = Result.failure(IllegalStateException("disk"))
                        break
                    }
                    result =
                        release.releaseRemotePasteData(paste, historyPull = true) {
                            if (write && paste == pastes.last()) writes++
                        }
                    if (result.isFailure) break
                    received.add(paste)
                }
                result
            }
        }

        private fun wire(pastes: List<PasteData>): SuccessResult {
            val serializer = ListSerializer(PasteData.serializer())
            return SuccessResult(store.json.decodeFromString(serializer, store.json.encodeToString(serializer, pastes)))
        }

        val service = PastePullService(cursor, board, api, sync)

        suspend fun seed() {
            cursor.settle("peer", 1)
        }
    }

    @Test fun `clock skew in either direction does not duplicate or skip new records`() =
        runTest {
            for (legacy in listOf(false, true)) {
                for (skew in listOf(-60000L, 60000L)) {
                    val h = Harness(legacy)
                    h.seed()
                    h.rows = listOf(pullTestPaste(2, nowEpochMilliseconds() + skew))
                    h.service.pullDevice("peer")
                    h.service.pullDevice("peer")
                    assertEquals(listOf(2L), h.received.map { it.id })
                    assertEquals(2L, h.cursor.getRemoteId("peer"))
                    assertEquals(0, h.writes)
                }
            }
        }

    @Test fun `sixty item backlog intentionally imports only newest fifty`() =
        runTest {
            val h = Harness()
            h.seed()
            h.rows = (2L..61L).map { pullTestPaste(it) }
            h.service.pullDevice("peer", 100)
            h.service.pullDevice("peer")
            assertEquals((12L..61L).toList(), h.received.map { it.id })
            assertTrue(h.received.zipWithNext().all { (a, b) -> a.createTime < b.createTime })
            coVerify(exactly = 2) { h.api.pullPasteBatchById("peer", any(), 50, any()) }
        }

    @Test fun `legacy fallback is cached and never sends local time`() =
        runTest {
            val h = Harness(true)
            h.seed()
            h.rows = listOf(pullTestPaste(2))
            h.service.pullDevice("peer")
            h.service.pullDevice("peer")
            coVerify(exactly = 1) { h.api.pullPasteBatchById(any(), any(), any(), any()) }
            coVerify(exactly = 2) { h.api.pullPasteBatch("peer", null, 50, any()) }
            h.info = h.info.copy(appVersion = "upgraded")
            h.service.pullDevice("peer")
            coVerify(exactly = 2) { h.api.pullPasteBatchById(any(), any(), any(), any()) }
        }

    @Test fun `transport and permission failures never fall back`() =
        runTest {
            val h = Harness()
            h.seed()
            coEvery { h.api.pullPasteBatchById(any(), any(), any(), any()) } returnsMany
                listOf(
                    ConnectionRefused,
                    createFailureResult(StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_USER, "denied"),
                )
            repeat(2) { h.service.pullDevice("peer") }
            assertEquals(1L, h.cursor.getRemoteId("peer"))
            coVerify(exactly = 0) { h.api.pullPasteBatch(any(), any(), any(), any()) }
            coVerify(exactly = 0) { h.board.tryWriteRemotePasteboardList(any(), any()) }
        }

    @Test fun `partial batch failure remains retryable after a newer live receive`() =
        runTest {
            val h = Harness()
            h.seed()
            h.rows = (2L..4L).map { pullTestPaste(it) }
            h.failId = 3
            h.service.pullDevice("peer")
            h.cursor.settle("peer", 5) // A later live message must not hide 3 or 4.
            assertEquals(2L, h.cursor.getQueryAfterId("peer"))
            h.failId = null
            h.service.pullDevice("peer")
            assertEquals(listOf(2L, 3L, 4L), h.received.map { it.id })
            assertEquals(5L, h.cursor.getQueryAfterId("peer"))
        }

    @Test fun `unknown disconnected denied and incompatible peers are not pulled`() =
        runTest {
            val h = Harness()
            h.service.pullDevice("peer")
            h.seed()
            h.info = h.info.copy(connectState = SyncState.DISCONNECTED)
            h.service.pullDevice("peer")
            h.info = h.info.copy(connectState = SyncState.CONNECTED, allowReceive = false)
            h.service.pullDevice("peer")
            h.info = h.info.copy(allowReceive = true)
            h.version = VersionRelation.LOWER_THAN
            h.service.pullDevice("peer")
            coVerify(exactly = 0) { h.api.pullPasteBatchById(any(), any(), any(), any()) }
        }

    @Test fun `peer identity is bound and clipboard opt-in writes only latest`() =
        runTest {
            val h = Harness()
            h.seed()
            every { h.store.config.getCurrentConfig() } returns TestAppConfig(enableAutoPullWriteClipboard = true)
            h.rows = listOf(pullTestPaste(2).copy(appInstanceId = "spoofed"), pullTestPaste(3))
            h.service.pullDevice("peer")
            assertTrue(h.received.all { it.appInstanceId == "peer" && it.remote })
            assertEquals(1, h.writes)
        }

    @Test fun `live sync completing after batch selection prevents a duplicate history write`() =
        runTest {
            val h = Harness()
            h.seed()
            h.rows = listOf(pullTestPaste(2))
            every { h.store.config.getCurrentConfig() } returns TestAppConfig(enableAutoPullWriteClipboard = true)
            var historyWrites = 0
            coEvery { h.board.tryWriteRemotePasteboardList(any(), any()) } coAnswers {
                val selected = firstArg<List<PasteData>>().single()
                assertTrue(h.release.releaseRemotePasteData(selected) {}.isSuccess)
                assertTrue(h.release.releaseRemotePasteData(selected, historyPull = true) { historyWrites++ }.isSuccess)
                Result.success(null)
            }
            h.service.pullDevice("peer")
            assertEquals(0, historyWrites)
            assertEquals(
                1,
                h.store.dao
                    .getRecentPasteDataByAppInstanceId(50)
                    .size,
            )
        }

    @Test fun `pulling all devices keeps a single clipboard write for the combined batch`() =
        runTest {
            val h = Harness()
            h.seed()
            h.cursor.settle("second", 1)
            h.rows = listOf(pullTestPaste(2))
            every { h.store.config.getCurrentConfig() } returns TestAppConfig(enableAutoPullWriteClipboard = true)
            every { h.sync.getSyncHandlers() } returns linkedMapOf("peer" to h.handler, "second" to h.handler)
            every { h.sync.getSyncHandler("second") } returns h.handler
            coEvery { h.api.pullPasteBatchById("second", any(), any(), any()) } returns
                SuccessResult(listOf(pullTestPaste(3).copy(appInstanceId = "second")))
            h.service.pullAllDevices()
            assertEquals(1, h.writes)
            assertEquals(setOf("peer", "second"), h.received.map { it.appInstanceId }.toSet())
            coVerify(exactly = 1) { h.board.tryWriteRemotePasteboardList(any(), true) }
        }
}
