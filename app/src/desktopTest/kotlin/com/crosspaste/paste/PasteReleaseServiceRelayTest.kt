package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.TestAppConfig
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.path.UserDataPathProvider
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

    @Test
    fun `remote non-file paste schedules a relay sync task for its source device`() =
        runBlocking<Unit> {
            val taskBuilder = mockk<TaskBuilder>(relaxed = true)
            val taskSubmitter =
                object : TaskSubmitter {
                    override suspend fun submit(block: suspend TaskBuilder.() -> Unit) {
                        taskBuilder.block()
                    }
                }
            val configManager = mockk<CommonConfigManager>(relaxed = true)
            every { configManager.getCurrentConfig() } returns TestAppConfig()
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.createPasteData(any(), any()) } returns 42L
            val service =
                PasteReleaseService(
                    commonConfigManager = configManager,
                    currentPaste = mockk(relaxed = true),
                    database = mockk<Database>(relaxed = true),
                    notificationManager = mockk(relaxed = true),
                    pasteDao = pasteDao,
                    pasteItemReader = mockk<PasteItemReader>(relaxed = true),
                    pasteProcessPlugins = emptyList(),
                    pastePullCursorManager = mockk(relaxed = true),
                    searchContentService = mockk(relaxed = true),
                    syncRuntimeInfoDao = mockk(relaxed = true),
                    taskSubmitter = taskSubmitter,
                    userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true),
                )
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

            val result = service.releaseRemotePasteData(pasteData) {}

            assertTrue(result.isSuccess)
            verify(exactly = 1) { taskBuilder.addRelaySyncTask(42L, "source-device") }
        }
}
