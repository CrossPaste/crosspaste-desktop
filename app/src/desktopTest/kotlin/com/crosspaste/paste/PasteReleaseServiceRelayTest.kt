package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class PasteReleaseServiceRelayTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val seen = setOf("origin-device", "sibling-device")

    private val taskBuilder = mockk<TaskBuilder>(relaxed = true)

    private val service =
        PasteReleaseService(
            commonConfigManager = configManager(),
            currentPaste = mockk(relaxed = true),
            database = mockk<Database>(relaxed = true),
            notificationManager = mockk(relaxed = true),
            pasteDao =
                mockk<PasteDao>(relaxed = true).also {
                    coEvery { it.createPasteData(any(), any()) } returns 42L
                },
            pasteItemReader = mockk<PasteItemReader>(relaxed = true),
            pasteProcessPlugins = emptyList(),
            pastePullCursorManager = mockk(relaxed = true),
            searchContentService = mockk(relaxed = true),
            syncRuntimeInfoDao = mockk(relaxed = true),
            taskSubmitter =
                object : TaskSubmitter {
                    override suspend fun submit(block: suspend TaskBuilder.() -> Unit) {
                        taskBuilder.block()
                    }
                },
            userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true),
        )

    private fun configManager(): CommonConfigManager {
        val appConfig = mockk<AppConfig>(relaxed = true)
        every { appConfig.maxNonFilePasteSize } returns 8L
        every { appConfig.maxBackupFileSize } returns 100L
        every { appConfig.largeFileDestinationPath } returns ""
        val configManager = mockk<CommonConfigManager>(relaxed = true)
        every { configManager.getCurrentConfig() } returns appConfig
        return configManager
    }

    private fun filePasteData(): PasteData {
        val filesItem =
            createFilesPasteItem(
                relativePathList = listOf("a.txt"),
                fileInfoTreeMap = mapOf("a.txt" to SingleFileInfoTree(size = 1024L, hash = "h")),
            )
        return PasteData(
            appInstanceId = "source-device",
            pasteAppearItem = filesItem,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.FILE_TYPE.type,
            source = null,
            size = 1024L,
            hash = "h",
            remote = true,
        )
    }

    @Test
    fun `remote non-file paste is relayed with the sender's seen set`() =
        runBlocking<Unit> {
            val item = createTextPasteItem(text = "relay me")
            val pasteData =
                PasteData(
                    appInstanceId = "source-device",
                    pasteAppearItem = item,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.TEXT_TYPE.type,
                    source = null,
                    size = item.size,
                    hash = item.hash,
                    remote = true,
                )

            val result = service.releaseRemotePasteData(pasteData, seen) {}

            assertTrue(result.isSuccess)
            verify(exactly = 1) { taskBuilder.addRelaySyncTask(42L, "source-device", seen) }
        }

    @Test
    fun `remote file paste defers its relay to the pull task and carries the seen set`() =
        runBlocking<Unit> {
            val result = service.releaseRemotePasteData(filePasteData(), seen) {}

            assertTrue(result.isSuccess)
            verify(exactly = 1) { taskBuilder.addPullFileTask(42L, any(), seen) }
            verify(exactly = 0) { taskBuilder.addRelaySyncTask(any(), any(), any()) }
        }
}
