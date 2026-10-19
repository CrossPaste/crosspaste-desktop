package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.TestAppConfig
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.sync.PastePullCursorManager
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteReleaseServiceRemoteDiscardTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val maxSizeBytes = 8L * 1024 * 1024

    private fun configManager(maxNonFilePasteSize: Long = 8): CommonConfigManager {
        val configManager = mockk<CommonConfigManager>(relaxed = true)
        every { configManager.getCurrentConfig() } returns
            TestAppConfig(maxNonFilePasteSize = maxNonFilePasteSize)
        return configManager
    }

    private fun newService(
        commonConfigManager: CommonConfigManager = configManager(),
        notificationManager: NotificationManager = mockk(relaxed = true),
        pasteDao: PasteDao = mockk(relaxed = true),
        pastePullCursorManager: PastePullCursorManager = mockk(relaxed = true),
        syncRuntimeInfoDao: SyncRuntimeInfoDao = mockk(relaxed = true),
        taskSubmitter: TaskSubmitter = mockk(relaxed = true),
    ): PasteReleaseService =
        PasteReleaseService(
            commonConfigManager = commonConfigManager,
            currentPaste = mockk(relaxed = true),
            database = mockk<Database>(relaxed = true),
            notificationManager = notificationManager,
            pasteDao = pasteDao,
            pasteItemReader = mockk<PasteItemReader>(relaxed = true),
            pasteProcessPlugins = emptyList(),
            pastePullCursorManager = pastePullCursorManager,
            searchContentService = mockk(relaxed = true),
            syncRuntimeInfoDao = syncRuntimeInfoDao,
            taskSubmitter = taskSubmitter,
            userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true),
        )

    /** Runs the submitted block so the clipboard/release side effects actually happen. */
    private fun immediateSubmitter(): TaskSubmitter =
        mockk<TaskSubmitter>().also { submitter ->
            coEvery { submitter.submit(any()) } coAnswers {
                firstArg<suspend TaskBuilder.() -> Unit>().invoke(mockk(relaxed = true))
            }
        }

    private fun remotePasteData(item: TextPasteItem): PasteData =
        PasteData(
            appInstanceId = "remote-device",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            source = null,
            size = item.size,
            hash = item.hash,
            remote = true,
        )

    private fun oversizedTextItem(): TextPasteItem =
        TextPasteItem(
            identifiers = listOf(),
            hash = "oversized",
            size = maxSizeBytes + 1,
            text = "oversized",
        )

    @Test
    fun `oversized remote non-file paste is skipped entirely with notification`() =
        runBlocking {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val syncRuntimeInfoDao = mockk<SyncRuntimeInfoDao>(relaxed = true)
            coEvery { syncRuntimeInfoDao.getSyncRuntimeInfo("remote-device") } returns null
            val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
            val service =
                newService(
                    notificationManager = notificationManager,
                    pasteDao = pasteDao,
                    pastePullCursorManager = pastePullCursorManager,
                    syncRuntimeInfoDao = syncRuntimeInfoDao,
                    taskSubmitter = taskSubmitter,
                )

            var wrotePasteboard = false
            val result =
                service.releaseRemotePasteData(remotePasteData(oversizedTextItem())) {
                    wrotePasteboard = true
                }

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow())
            assertTrue(!wrotePasteboard)
            coVerify(exactly = 0) { taskSubmitter.submit(any()) }
            coVerify(exactly = 1) {
                pastePullCursorManager.settle(
                    appInstanceId = "remote-device",
                    remotePasteId = any(),
                )
            }
            verify(exactly = 1) { notificationManager.sendNotification(any(), any(), any(), any()) }
        }

    @Test
    fun `remote paste within limit is released normally`() =
        runBlocking {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val taskSubmitter = immediateSubmitter()
            val service =
                newService(
                    notificationManager = notificationManager,
                    taskSubmitter = taskSubmitter,
                )

            var wrotePasteboard = false
            val result =
                service.releaseRemotePasteData(remotePasteData(createTextPasteItem(text = "small"))) {
                    wrotePasteboard = true
                }

            assertTrue(result.isSuccess)
            assertNotNull(result.getOrThrow())
            assertTrue(wrotePasteboard)
            coVerify(exactly = 1) { taskSubmitter.submit(any()) }
            verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
        }

    @Test
    fun `raised limit releases previously oversized remote paste normally`() =
        runBlocking {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
            val service =
                newService(
                    commonConfigManager = configManager(maxNonFilePasteSize = 64),
                    notificationManager = notificationManager,
                    taskSubmitter = taskSubmitter,
                )

            val result = service.releaseRemotePasteData(remotePasteData(oversizedTextItem())) {}

            assertTrue(result.isSuccess)
            assertNotNull(result.getOrThrow())
            coVerify(exactly = 1) { taskSubmitter.submit(any()) }
            verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
        }

    @Test
    fun `remote paste with invalid content is discarded without touching the clipboard`() =
        runBlocking {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val taskSubmitter = immediateSubmitter()
            val service =
                newService(
                    notificationManager = notificationManager,
                    pastePullCursorManager = pastePullCursorManager,
                    taskSubmitter = taskSubmitter,
                )

            var wrotePasteboard = false
            val result =
                service.releaseRemotePasteData(
                    remotePasteData(createTextPasteItem(text = "")).copy(id = 100L),
                ) {
                    wrotePasteboard = true
                }

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow())
            assertTrue(!wrotePasteboard)
            coVerify(exactly = 0) { taskSubmitter.submit(any()) }
            coVerify(exactly = 1) { pastePullCursorManager.settle("remote-device", 100L) }
            verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
        }

    /**
     * Passes validateFileTransferMetadata (names match the tree, sizes add up) but a
     * zero-byte file fails PasteFiles.isValid(), so only the content check rejects it.
     */
    private fun contentInvalidFilePaste(): PasteData {
        val item =
            createFilesPasteItem(
                relativePathList = listOf("empty.bin"),
                fileInfoTreeMap = mapOf("empty.bin" to SingleFileInfoTree(0, "empty")),
            )
        return PasteData(
            appInstanceId = "remote-device",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.FILE_TYPE.type,
            source = null,
            size = item.size,
            hash = item.hash,
            remote = true,
        ).copy(id = 100L)
    }

    @Test
    fun `pulled file paste with invalid content is discarded before any download`() =
        runBlocking {
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val taskSubmitter = immediateSubmitter()
            val service =
                newService(
                    pastePullCursorManager = pastePullCursorManager,
                    taskSubmitter = taskSubmitter,
                )

            var wrotePasteboard = false
            val result =
                service.releaseRemotePasteData(contentInvalidFilePaste()) {
                    wrotePasteboard = true
                }

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow())
            assertTrue(!wrotePasteboard)
            coVerify(exactly = 0) { taskSubmitter.submit(any()) }
            coVerify(exactly = 1) { pastePullCursorManager.settle("remote-device", 100L) }
        }

    @Test
    fun `pushed file paste with invalid content is rejected before a row is created`() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            coEvery { pastePullCursorManager.hasActiveReceive(any()) } returns false
            val service =
                newService(
                    pasteDao = pasteDao,
                    pastePullCursorManager = pastePullCursorManager,
                )

            val prepared = service.releaseRemotePasteDataForPush(contentInvalidFilePaste())

            assertNull(prepared)
            coVerify(exactly = 0) { pastePullCursorManager.reserve(any(), any()) }
            coVerify(exactly = 0) { pasteDao.createPasteData(any(), any()) }
        }

    @Test
    fun `invalid remote file paste is discarded and the batch continues`() =
        runBlocking {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
            val service =
                newService(
                    notificationManager = notificationManager,
                    pastePullCursorManager = pastePullCursorManager,
                    taskSubmitter = taskSubmitter,
                )
            val invalidFilePaste =
                PasteData(
                    appInstanceId = "remote-device",
                    pasteAppearItem =
                        createFilesPasteItem(
                            relativePathList = emptyList(),
                            fileInfoTreeMap = emptyMap(),
                        ),
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.FILE_TYPE.type,
                    source = null,
                    size = 0L,
                    hash = "",
                    remote = true,
                ).copy(id = 100L, createTime = 5000000000000L)
            val laterTextPaste =
                remotePasteData(createTextPasteItem(text = "after")).copy(createTime = 200L)

            val result =
                service.releaseRemotePasteDataList(listOf(invalidFilePaste, laterTextPaste)) {
                    service.releaseRemotePasteData(it) { _ -> }
                }

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                pastePullCursorManager.settle(
                    appInstanceId = "remote-device",
                    remotePasteId = 100L,
                )
            }
            verify(exactly = 1) { notificationManager.sendNotification(any(), any(), any(), any()) }
            coVerify(exactly = 1) { taskSubmitter.submit(any()) }
        }

    @Test
    fun `batch stops before a later discard when an earlier paste fails`() =
        runBlocking {
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
            coEvery { taskSubmitter.submit(any()) } throws IllegalStateException("database write failed")
            val service =
                newService(
                    pastePullCursorManager = pastePullCursorManager,
                    taskSubmitter = taskSubmitter,
                )
            val normalPaste =
                remotePasteData(
                    createTextPasteItem(text = "small"),
                ).copy(id = 100L, createTime = 5000000000000L)
            val laterDiscard = remotePasteData(oversizedTextItem()).copy(createTime = 200L)

            val result =
                service.releaseRemotePasteDataList(listOf(normalPaste, laterDiscard)) {
                    service.releaseRemotePasteData(it) { _ -> }
                }

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { pastePullCursorManager.settle(any(), any()) }
        }

    @Test
    fun `a remote non-file paste that lands durably advances the persisted pull cursor`() =
        runBlocking {
            val pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true)
            val taskSubmitter = mockk<TaskSubmitter>()
            coEvery { taskSubmitter.submit(any()) } coAnswers {
                firstArg<suspend TaskBuilder.() -> Unit>().invoke(mockk(relaxed = true))
            }
            val service =
                newService(
                    pastePullCursorManager = pastePullCursorManager,
                    taskSubmitter = taskSubmitter,
                )
            val pasteData =
                remotePasteData(
                    createTextPasteItem(text = "small"),
                ).copy(id = 100L, createTime = 5000000000000L)

            val result = service.releaseRemotePasteData(pasteData) { _ -> }

            assertTrue(result.isSuccess)
            // Durable, so deleting the paste later cannot rewind the cursor and pull it back
            coVerify(exactly = 1) { pastePullCursorManager.settle("remote-device", 100L) }
        }

    @Test
    fun `receives interrupted by the last shutdown are discarded`() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.getRemoteLoadingPasteIds() } returns listOf(3L, 5L)
            coEvery { pasteDao.markDeleteFailedReceive(any()) } returns Result.success(Unit)
            val service = newService(pasteDao = pasteDao)

            service.discardInterruptedReceives()

            coVerify(exactly = 1) { pasteDao.markDeleteFailedReceive(3L) }
            coVerify(exactly = 1) { pasteDao.markDeleteFailedReceive(5L) }
            // The pre-allocated files outside managed storage are looked up per row
            coVerify(exactly = 1) { pasteDao.getLoadingPasteData(3L) }
            coVerify(exactly = 1) { pasteDao.getLoadingPasteData(5L) }
        }

    @Test
    fun `deleteUnfinishedReceiveFiles falls back to deleted row if already marked deleted`() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val provider = mockk<UserDataPathProvider>(relaxed = true)
            val remotePaste = remotePasteData(oversizedTextItem())
            coEvery { pasteDao.getLoadingPasteData(42L) } returns null
            coEvery { pasteDao.getDeletePasteData(42L) } returns remotePaste

            deleteUnfinishedReceiveFiles(pasteDao, provider, 42L)

            verify(exactly = 1) { provider.deleteReceivedFilesOutsideStorage(remotePaste) }
        }
}
