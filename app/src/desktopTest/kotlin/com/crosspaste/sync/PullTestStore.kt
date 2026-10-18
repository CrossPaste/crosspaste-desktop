package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.TestAppConfig
import com.crosspaste.db.DriverFactory
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.SqlPasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

internal class PullTestStore(
    val driverFactory: DriverFactory = TestDriverFactory(),
) {
    val json = getJsonUtils().JSON
    val database = createDatabase(driverFactory)
    val tasks = mockk<TaskBuilder>(relaxed = true)
    val submitter =
        object : TaskSubmitter {
            override suspend fun submit(block: suspend TaskBuilder.() -> Unit) {
                tasks.block()
            }
        }
    val dao =
        SqlPasteDao(
            AppInfo("peer", "1", "1", "test"),
            database,
            DefaultPasteItemReader(),
            mockk(relaxed = true),
            submitter,
            mockk(relaxed = true),
        )
    var peerExists = true
    private val syncDao =
        mockk<SyncRuntimeInfoDao>().also {
            coEvery { it.getSyncRuntimeInfo(any()) } answers { if (peerExists) mockk(relaxed = true) else null }
        }

    fun cursor() = PastePullCursorManager(dao, syncDao)

    val config =
        mockk<CommonConfigManager>().also {
            every { it.getCurrentConfig() } returns TestAppConfig()
        }

    fun release(cursor: PastePullCursorManager) =
        PasteReleaseService(
            config,
            mockk(relaxed = true),
            database,
            mockk(relaxed = true),
            dao,
            DefaultPasteItemReader(),
            emptyList(),
            cursor,
            mockk(relaxed = true),
            syncDao,
            submitter,
            mockk(relaxed = true),
        )
}

internal fun pullTestPaste(
    id: Long,
    time: Long = 0L,
): PasteData {
    val item = createTextPasteItem(text = "paste-$id")
    return PasteData(
        id = id,
        appInstanceId = "peer",
        pasteAppearItem = item,
        pasteCollection = PasteCollection(emptyList()),
        pasteType = PasteType.TEXT_TYPE.type,
        size = item.size,
        hash = item.hash,
        createTime = time,
    )
}
