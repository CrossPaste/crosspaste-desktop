package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
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

class SharePushOrchestratorTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private class TestDeps {
        val pasteClientApi: PasteClientApi = mockk(relaxed = true)
        val filePushService: FilePushService = mockk(relaxed = true)
        val syncRuntimeInfoDao: SyncRuntimeInfoDao = mockk(relaxed = true)

        fun create(): SharePushOrchestrator =
            SharePushOrchestrator(
                pasteClientApi = pasteClientApi,
                filePushService = filePushService,
                syncRuntimeInfoDao = syncRuntimeInfoDao,
            )
    }

    private fun pasteOf(type: PasteType): PasteData {
        val pasteData = mockk<PasteData>(relaxed = true)
        every { pasteData.id } returns 1L
        every { pasteData.getType() } returns type
        every { pasteData.isFileType() } returns (type == PasteType.FILE_TYPE || type == PasteType.IMAGE_TYPE)
        return pasteData
    }

    private fun retryableFailure(message: String): FailureResult =
        FailureResult(
            PasteException(
                StandardErrorCode.SYNC_PASTE_ERROR.toErrorCode(),
                message,
            ),
        )

    @Test
    fun `no eligible targets returns empty result`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns emptyList()

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.TEXT_TYPE))

            assertTrue(result.perTarget.isEmpty())
            assertFalse(result.allSucceeded, "empty fan-out is not 'all succeeded'")
            assertFalse(result.anySucceeded)
            coVerify(exactly = 0) { deps.pasteClientApi.sendPaste(any(), any(), any()) }
            coVerify(exactly = 0) { deps.filePushService.pushFiles(any(), any(), any()) }
        }

    @Test
    fun `filters out non-CONNECTED and disallowSend targets`() =
        runTest {
            val deps = TestDeps()
            val connected = createConnectedSyncRuntimeInfo(appInstanceId = "remote-1")
            val disconnected =
                createSyncRuntimeInfo(
                    appInstanceId = "remote-2",
                    connectHostAddress = "192.168.1.101",
                    connectState = SyncState.DISCONNECTED,
                )
            val connectedButDisallowed =
                createConnectedSyncRuntimeInfo(appInstanceId = "remote-3").copy(allowSend = false)
            val unverified =
                createSyncRuntimeInfo(
                    appInstanceId = "remote-4",
                    connectHostAddress = "192.168.1.103",
                    connectState = SyncState.UNVERIFIED,
                )

            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
                listOf(connected, disconnected, connectedButDisallowed, unverified)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns SuccessResult()

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.TEXT_TYPE))

            assertEquals(setOf("remote-1"), result.perTarget.keys)
            assertTrue(result.allSucceeded)
            coVerify(exactly = 1) { deps.pasteClientApi.sendPaste(any(), eq("remote-1"), any()) }
            coVerify(exactly = 0) { deps.pasteClientApi.sendPaste(any(), eq("remote-2"), any()) }
            coVerify(exactly = 0) { deps.pasteClientApi.sendPaste(any(), eq("remote-3"), any()) }
            coVerify(exactly = 0) { deps.pasteClientApi.sendPaste(any(), eq("remote-4"), any()) }
        }

    @Test
    fun `file type routes to filePushService`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
                listOf(createConnectedSyncRuntimeInfo(appInstanceId = "remote-1"))
            coEvery { deps.filePushService.pushFiles(any(), any(), any()) } returns SuccessResult()

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.FILE_TYPE))

            assertTrue(result.allSucceeded)
            coVerify(exactly = 1) { deps.filePushService.pushFiles(any(), eq("remote-1"), any()) }
            coVerify(exactly = 0) { deps.pasteClientApi.sendPaste(any(), any(), any()) }
        }

    @Test
    fun `text type routes to pasteClientApi sendPaste`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
                listOf(createConnectedSyncRuntimeInfo(appInstanceId = "remote-1"))
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns SuccessResult()

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.TEXT_TYPE))

            assertTrue(result.allSucceeded)
            coVerify(exactly = 1) { deps.pasteClientApi.sendPaste(any(), eq("remote-1"), any()) }
            coVerify(exactly = 0) { deps.filePushService.pushFiles(any(), any(), any()) }
        }

    @Test
    fun `multi-target fan-out all succeed`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
                listOf(
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-1"),
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-2", hostAddress = "192.168.1.101"),
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-3", hostAddress = "192.168.1.102"),
                )
            coEvery { deps.filePushService.pushFiles(any(), any(), any()) } returns SuccessResult()

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.FILE_TYPE))

            assertEquals(setOf("remote-1", "remote-2", "remote-3"), result.perTarget.keys)
            assertTrue(result.allSucceeded)
            assertTrue(result.anySucceeded)
            coVerify(exactly = 3) { deps.filePushService.pushFiles(any(), any(), any()) }
        }

    @Test
    fun `multi-target fan-out partial failure`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
                listOf(
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-1"),
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-2", hostAddress = "192.168.1.101"),
                )
            coEvery {
                deps.filePushService.pushFiles(any(), eq("remote-1"), any())
            } returns SuccessResult()
            coEvery {
                deps.filePushService.pushFiles(any(), eq("remote-2"), any())
            } returns retryableFailure("chunk upload failed")

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.FILE_TYPE))

            assertEquals(setOf("remote-1", "remote-2"), result.perTarget.keys)
            assertFalse(result.allSucceeded)
            assertTrue(result.anySucceeded)
            assertTrue(result.perTarget["remote-1"] is SuccessResult)
            assertTrue(result.perTarget["remote-2"] is FailureResult)
        }

    @Test
    fun `multi-target fan-out all fail`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
                listOf(
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-1"),
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-2", hostAddress = "192.168.1.101"),
                )
            coEvery {
                deps.filePushService.pushFiles(any(), any(), any())
            } returns retryableFailure("network down")

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.FILE_TYPE))

            assertFalse(result.allSucceeded)
            assertFalse(result.anySucceeded)
            assertTrue(result.perTarget.values.all { it is FailureResult })
        }

    @Test
    fun `null connectHostAddress fails that target gracefully without affecting others`() =
        runTest {
            val deps = TestDeps()
            val withHost = createConnectedSyncRuntimeInfo(appInstanceId = "remote-1")
            // CONNECTED + allowSend but somehow connectHostAddress is null —
            // shouldn't crash the whole fan-out.
            val withoutHost = createConnectedSyncRuntimeInfo(appInstanceId = "remote-2").copy(connectHostAddress = null)

            coEvery { deps.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns listOf(withHost, withoutHost)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns SuccessResult()

            val result = deps.create().pushToConnectedPeers(pasteOf(PasteType.TEXT_TYPE))

            assertEquals(setOf("remote-1", "remote-2"), result.perTarget.keys)
            assertTrue(result.perTarget["remote-1"] is SuccessResult)
            assertTrue(result.perTarget["remote-2"] is FailureResult)
            assertFalse(result.allSucceeded)
            assertTrue(result.anySucceeded)
        }
}
