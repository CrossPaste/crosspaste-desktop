package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.SqlPasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteReleaseServiceImageDedupTest {

    // Eagerly initialize JsonUtils to avoid circular class initialization between
    // PasteItem.Companion (which calls getJsonUtils()) and paste item classes
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val appInfo =
        AppInfo(
            appInstanceId = "test-instance",
            appVersion = "1.0.0",
            appRevision = "abc",
            userName = "testUser",
        )

    private val searchContentService =
        object : SearchContentService {
            override fun createSearchContent(
                source: String?,
                searchContentList: List<String>,
            ): String =
                (
                    listOfNotNull(source?.lowercase()) +
                        searchContentList.map { it.lowercase() }
                ).joinToString(" ")

            override fun createSearchTerms(queryString: String): List<String> =
                queryString.trim().split("\\s+".toRegex()).filter {
                    it.isNotBlank()
                }
        }

    // Thread-safe: the concurrent-release test records from parallel coroutines.
    private class RecordingTaskBuilder : TaskBuilder {
        val deleteIds = CopyOnWriteArrayList<Long>()
        val syncTaskCount = AtomicInteger(0)
        val renderingTaskCount = AtomicInteger(0)

        override fun addDelayedDeletePasteTask(
            id: Long,
            delayMillis: Long,
        ): TaskBuilder = this

        override fun addDeletePasteTasks(ids: List<Long>): TaskBuilder {
            deleteIds.addAll(ids)
            return this
        }

        override fun addPullFileTask(
            id: Long,
            remotePasteDataId: Long,
        ): TaskBuilder = this

        override fun addSyncTask(
            id: Long,
            fileSize: Long,
            appInstanceId: String,
            targetAppInstanceIds: Set<String>?,
        ): TaskBuilder {
            syncTaskCount.incrementAndGet()
            return this
        }

        override fun addRelaySyncTask(
            id: Long,
            appInstanceId: String,
        ): TaskBuilder = this

        override fun addPullIconTask(
            id: Long,
            existIconFile: Boolean,
        ): TaskBuilder = this

        override fun addRenderingTask(
            id: Long,
            pasteType: PasteType,
        ): TaskBuilder {
            renderingTaskCount.incrementAndGet()
            return this
        }
    }

    private class ImmediateTaskSubmitter : TaskSubmitter {
        val builder = RecordingTaskBuilder()

        override suspend fun submit(block: suspend TaskBuilder.() -> Unit) {
            builder.block()
        }
    }

    private class Fixture {
        val taskSubmitter = ImmediateTaskSubmitter()
        val currentPaste = mockk<CurrentPaste>(relaxed = true)
        val database = createDatabase(TestDriverFactory())
        private val pasteItemReader = DefaultPasteItemReader()

        lateinit var pasteDao: SqlPasteDao
        lateinit var service: PasteReleaseService

        fun init(
            appInfo: AppInfo,
            searchContentService: SearchContentService,
        ) {
            pasteDao =
                SqlPasteDao(
                    appInfo = appInfo,
                    database = database,
                    pasteItemReader = pasteItemReader,
                    searchContentService = searchContentService,
                    taskSubmitter = taskSubmitter,
                    userDataPathProvider = mockk(relaxed = true),
                )
            service =
                PasteReleaseService(
                    commonConfigManager = mockk(relaxed = true),
                    currentPaste = currentPaste,
                    database = database,
                    notificationManager = mockk(relaxed = true),
                    pasteDao = pasteDao,
                    pasteItemReader = pasteItemReader,
                    pasteProcessPlugins = emptyList(),
                    pastePullCursorManager = mockk(relaxed = true),
                    searchContentService = searchContentService,
                    syncRuntimeInfoDao = mockk(relaxed = true),
                    taskSubmitter = taskSubmitter,
                    userDataPathProvider = mockk(relaxed = true),
                )
        }
    }

    private fun newFixture(): Fixture = Fixture().apply { init(appInfo, searchContentService) }

    private fun imageItem(fileHash: String = "file-hash"): ImagesPasteItem =
        createImagesPasteItem(
            identifiers = listOf("image/png"),
            relativePathList = listOf("img/a.png"),
            fileInfoTreeMap = mapOf("a.png" to SingleFileInfoTree(size = 10L, hash = fileHash)),
        )

    private suspend fun Fixture.createLoadedRecord(
        item: PasteItem,
        pasteType: PasteType,
        createTime: Long,
        remote: Boolean = false,
    ): Long =
        pasteDao.createPasteData(
            PasteData(
                appInstanceId = if (remote) "remote-device" else appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(emptyList()),
                pasteType = pasteType.type,
                size = item.size,
                hash = item.hash,
                createTime = createTime,
                pasteState = PasteState.LOADED,
                remote = remote,
            ),
        )

    private suspend fun Fixture.createLoadingRecord(createTime: Long = DateUtils.nowEpochMilliseconds()): Long =
        pasteDao.createPasteData(
            PasteData(
                appInstanceId = appInfo.appInstanceId,
                pasteCollection = PasteCollection(emptyList()),
                pasteType = PasteType.INVALID_TYPE.type,
                size = 0L,
                hash = "",
                createTime = createTime,
                pasteState = PasteState.LOADING,
            ),
        )

    @Test
    fun `duplicate local image within window is discarded keeping first record`() =
        runTest {
            val fixture = newFixture()
            val item = imageItem()
            val keptId =
                fixture.createLoadedRecord(item, PasteType.IMAGE_TYPE, DateUtils.nowEpochMilliseconds())
            val loadingId = fixture.createLoadingRecord()

            fixture.service.releaseLocalPasteData(loadingId, listOf(imageItem()), null)

            // Second record is marked deleted with the final item persisted, so the
            // delete task can reclaim the just-written image files.
            val discarded = fixture.pasteDao.getDeletePasteData(loadingId)
            assertNotNull(discarded)
            val discardedItem = discarded.pasteAppearItem as ImagesPasteItem
            assertContentEquals(listOf("img/a.png"), discardedItem.relativePathList)
            assertEquals(item.hash, discarded.hash)

            // Only a delete task for the discarded record — never sync or rendering.
            assertContentEquals(listOf(loadingId), fixture.taskSubmitter.builder.deleteIds)
            assertEquals(
                0,
                fixture.taskSubmitter.builder.syncTaskCount
                    .get(),
            )
            assertEquals(
                0,
                fixture.taskSubmitter.builder.renderingTaskCount
                    .get(),
            )
            coVerify(exactly = 0) { fixture.currentPaste.setPasteId(any()) }

            // First record is untouched.
            assertNotNull(fixture.pasteDao.getNoDeletePasteDataBlock(keptId))
        }

    @Test
    fun `identical image outside dedup window is kept`() =
        runTest {
            val fixture = newFixture()
            val outsideWindow =
                DateUtils.nowEpochMilliseconds() -
                    PasteReleaseService.IMAGE_DEDUP_WINDOW.inWholeMilliseconds - 1000L
            fixture.createLoadedRecord(imageItem(), PasteType.IMAGE_TYPE, outsideWindow)
            val loadingId = fixture.createLoadingRecord()

            fixture.service.releaseLocalPasteData(loadingId, listOf(imageItem()), null)

            val released = fixture.pasteDao.getNoDeletePasteDataBlock(loadingId)
            assertNotNull(released)
            assertEquals(PasteState.LOADED, released.pasteState)
            assertTrue(
                fixture.taskSubmitter.builder.deleteIds
                    .isEmpty(),
            )
            assertEquals(
                1,
                fixture.taskSubmitter.builder.syncTaskCount
                    .get(),
            )
            assertEquals(
                1,
                fixture.taskSubmitter.builder.renderingTaskCount
                    .get(),
            )
        }

    @Test
    fun `remote record with same hash does not trigger dedup`() =
        runTest {
            val fixture = newFixture()
            fixture.createLoadedRecord(
                imageItem(),
                PasteType.IMAGE_TYPE,
                DateUtils.nowEpochMilliseconds(),
                remote = true,
            )
            val loadingId = fixture.createLoadingRecord()

            fixture.service.releaseLocalPasteData(loadingId, listOf(imageItem()), null)

            val released = fixture.pasteDao.getNoDeletePasteDataBlock(loadingId)
            assertNotNull(released)
            assertEquals(PasteState.LOADED, released.pasteState)
            assertTrue(
                fixture.taskSubmitter.builder.deleteIds
                    .isEmpty(),
            )
        }

    @Test
    fun `text pastes keep existing same-hash cleanup semantics`() =
        runTest {
            val fixture = newFixture()
            val textItem = createTextPasteItem(text = "hello")
            val oldId =
                fixture.createLoadedRecord(textItem, PasteType.TEXT_TYPE, DateUtils.nowEpochMilliseconds())
            val loadingId = fixture.createLoadingRecord()

            fixture.service.releaseLocalPasteData(loadingId, listOf(createTextPasteItem(text = "hello")), null)

            // Existing behavior: the new record is kept and the old same-hash
            // record is removed (keep-new), untouched by image keep-first dedup.
            val released = fixture.pasteDao.getNoDeletePasteDataBlock(loadingId)
            assertNotNull(released)
            assertEquals(PasteState.LOADED, released.pasteState)
            assertContentEquals(listOf(oldId), fixture.taskSubmitter.builder.deleteIds)
        }

    @Test
    fun `dedup query ignores deleted records`() =
        runTest {
            val fixture = newFixture()
            val item = imageItem()
            fixture.pasteDao.createPasteData(
                PasteData(
                    appInstanceId = appInfo.appInstanceId,
                    pasteAppearItem = item,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.IMAGE_TYPE.type,
                    size = item.size,
                    hash = item.hash,
                    pasteState = PasteState.DELETED,
                ),
            )

            assertNull(
                fixture.pasteDao.getRecentSameHashLocalPasteId(item.hash, PasteType.IMAGE_TYPE.type, 0L, -1L),
            )
        }

    @Test
    fun `loading row with same hash is not a kept candidate`() =
        runTest {
            val fixture = newFixture()
            val item = imageItem()
            // A LOADING row that (contrary to the current convention of an empty
            // placeholder hash) already carries the final hash must still not be
            // treated as a kept candidate: it is not fully released yet.
            fixture.pasteDao.createPasteData(
                PasteData(
                    appInstanceId = appInfo.appInstanceId,
                    pasteAppearItem = item,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.IMAGE_TYPE.type,
                    size = item.size,
                    hash = item.hash,
                    pasteState = PasteState.LOADING,
                ),
            )

            assertNull(
                fixture.pasteDao.getRecentSameHashLocalPasteId(item.hash, PasteType.IMAGE_TYPE.type, 0L, -1L),
            )
        }

    @Test
    fun `dedup window compares record create times not release time`() =
        runTest {
            val fixture = newFixture()
            val now = DateUtils.nowEpochMilliseconds()
            // Both records were created ~8s ago, only 50ms apart — slow image
            // processing delayed the release, but they are still duplicates of a
            // single clipboard operation and must be deduped.
            fixture.createLoadedRecord(imageItem(), PasteType.IMAGE_TYPE, now - 8000L)
            val loadingId = fixture.createLoadingRecord(createTime = now - 7950L)

            fixture.service.releaseLocalPasteData(loadingId, listOf(imageItem()), null)

            assertNotNull(fixture.pasteDao.getDeletePasteData(loadingId))
            assertContentEquals(listOf(loadingId), fixture.taskSubmitter.builder.deleteIds)
        }

    @Test
    fun `concurrent duplicate releases keep exactly one record`() =
        runTest {
            val fixture = newFixture()
            val firstId = fixture.createLoadingRecord()
            val secondId = fixture.createLoadingRecord()

            listOf(firstId, secondId)
                .map { id ->
                    async(Dispatchers.Default) {
                        fixture.service.releaseLocalPasteData(id, listOf(imageItem()), null)
                    }
                }.awaitAll()

            val loadedIds =
                listOf(firstId, secondId).filter {
                    fixture.pasteDao.getNoDeletePasteDataBlock(it) != null
                }
            assertEquals(1, loadedIds.size)
            val discardedId = (listOf(firstId, secondId) - loadedIds.toSet()).single()
            assertNotNull(fixture.pasteDao.getDeletePasteData(discardedId))
            assertContentEquals(listOf(discardedId), fixture.taskSubmitter.builder.deleteIds)
        }
}
