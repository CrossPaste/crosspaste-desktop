package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class PasteReleaseServicePushTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun newService(
        pasteDao: PasteDao = mockk(relaxed = true),
        commonConfigManager: CommonConfigManager = defaultConfigManager(),
        userDataPathProvider: UserDataPathProvider = mockk(relaxed = true),
    ): PasteReleaseService =
        PasteReleaseService(
            commonConfigManager = commonConfigManager,
            currentPaste = mockk(relaxed = true),
            database = mockk<Database>(relaxed = true),
            pasteDao = pasteDao,
            pasteItemReader = mockk<PasteItemReader>(relaxed = true),
            pasteProcessPlugins = emptyList(),
            searchContentService = mockk(relaxed = true),
            taskSubmitter = mockk<TaskSubmitter>(relaxed = true),
            userDataPathProvider = userDataPathProvider,
        )

    private fun defaultConfigManager(): CommonConfigManager {
        val appConfig = mockk<AppConfig>(relaxed = true)
        every { appConfig.maxBackupFileSize } returns 100L // 100 MB after bytesSize() multiply
        val configManager = mockk<CommonConfigManager>(relaxed = true)
        every { configManager.getCurrentConfig() } returns appConfig
        return configManager
    }

    @Test
    fun releaseRemotePasteDataForPush_markDeletesWhenFilesIndexEmpty() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.createPasteData(any(), any()) } returns 99L
            coEvery { pasteDao.markDeletePasteData(any()) } returns Result.success(Unit)

            // userDataPathProvider.resolve is called by buildFilesIndex with the FilesPasteItem
            // but relaxed=true makes it a no-op — builder stays empty → FilesIndex has 0 chunks
            // → triggers the empty-filesIndex cleanup branch in releaseRemotePasteDataForPush.
            val service = newService(pasteDao = pasteDao)

            val emptyFilesItem =
                createFilesPasteItem(
                    relativePathList = emptyList(),
                    fileInfoTreeMap = emptyMap(),
                )
            val pasteData =
                PasteData(
                    appInstanceId = "test-mobile",
                    pasteAppearItem = emptyFilesItem,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.FILE_TYPE.type,
                    source = null,
                    size = 0L,
                    hash = "",
                )

            val result = service.releaseRemotePasteDataForPush(pasteData)

            assertNull(result, "empty filesIndex should yield null result")
            coVerify(exactly = 1) { pasteDao.markDeletePasteData(99L) }
        }
}
