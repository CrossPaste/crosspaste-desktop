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
import com.crosspaste.sync.PastePullCursorManager
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
            assertTrue(!wrotePasteboard)
            coVerify(exactly = 0) { taskSubmitter.submit(any()) }
            coVerify(exactly = 1) {
                pastePullCursorManager.persistDiscardedMaxCreateTime(
                    appInstanceId = "remote-device",
                    createTime = any(),
                )
            }
            verify(exactly = 1) { notificationManager.sendNotification(any(), any(), any(), any()) }
        }

    @Test
    fun `remote paste within limit is released normally`() =
        runBlocking {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
            val service =
                newService(
                    notificationManager = notificationManager,
                    taskSubmitter = taskSubmitter,
                )

            val result =
                service.releaseRemotePasteData(remotePasteData(createTextPasteItem(text = "small"))) {}

            assertTrue(result.isSuccess)
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
            coVerify(exactly = 1) { taskSubmitter.submit(any()) }
            verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
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
                ).copy(createTime = 100L)
            val laterTextPaste =
                remotePasteData(createTextPasteItem(text = "after")).copy(createTime = 200L)

            val result =
                service.releaseRemotePasteDataList(listOf(invalidFilePaste, laterTextPaste)) {
                    service.releaseRemotePasteData(it) { _ -> }
                }

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                pastePullCursorManager.persistDiscardedMaxCreateTime(
                    appInstanceId = "remote-device",
                    createTime = 100L,
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
            val normalPaste = remotePasteData(createTextPasteItem(text = "small")).copy(createTime = 100L)
            val laterDiscard = remotePasteData(oversizedTextItem()).copy(createTime = 200L)

            val result =
                service.releaseRemotePasteDataList(listOf(normalPaste, laterDiscard)) {
                    service.releaseRemotePasteData(it) { _ -> }
                }

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { pastePullCursorManager.persistDiscardedMaxCreateTime(any(), any()) }
        }
}
