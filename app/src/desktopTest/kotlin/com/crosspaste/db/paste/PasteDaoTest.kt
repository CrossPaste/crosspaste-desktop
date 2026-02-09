package com.crosspaste.db.paste

import app.cash.turbine.test
import com.crosspaste.app.AppInfo
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteDaoTest {

    // Eagerly initialize JsonUtils to avoid circular class initialization between
    // PasteItem.Companion (which calls getJsonUtils()) and TextPasteItem
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val appInfo = AppInfo(
        appInstanceId = "test-instance",
        appVersion = "1.0.0",
        appRevision = "abc",
        userName = "testUser",
    )

    private val searchContentService = object : SearchContentService {
        override fun createSearchContent(source: String?, searchContentList: List<String>): String {
            return (listOfNotNull(source?.lowercase()) + searchContentList.map { it.lowercase() }).joinToString(" ")
        }

        override fun createSearchTerms(queryString: String): List<String> {
            return queryString.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        }
    }

    private val currentPaste: CurrentPaste = mockk(relaxed = true)
    private val taskSubmitter: TaskSubmitter = mockk(relaxed = true)
    private val userDataPathProvider = mockk<com.crosspaste.path.UserDataPathProvider>(relaxed = true)

    private val database = createDatabase(TestDriverFactory())

    private val pasteDao = PasteDao(
        appInfo = appInfo,
        currentPaste = currentPaste,
        database = database,
        pasteProcessPlugins = listOf(),
        searchContentService = searchContentService,
        taskSubmitter = taskSubmitter,
        userDataPathProvider = userDataPathProvider,
    )

    private fun createTestPasteData(
        text: String = "hello world",
        pasteType: PasteType = PasteType.TEXT_TYPE,
        appInstanceId: String = "test-instance",
        source: String? = null,
        favorite: Boolean = false,
    ): PasteData {
        val textItem = createTextPasteItem(
            identifiers = listOf(DesktopTextTypePlugin.TEXT),
            text = text,
        )
        return PasteData(
            appInstanceId = appInstanceId,
            favorite = favorite,
            pasteAppearItem = textItem,
            pasteCollection = PasteCollection(listOf()),
            pasteType = pasteType.type,
            source = source,
            size = textItem.size,
            hash = textItem.hash,
            pasteState = PasteState.LOADED,
            createTime = DateUtils.nowEpochMilliseconds(),
        )
    }

    // --- Create and retrieve ---

    @Test
    fun `createPasteData returns valid id`() = runTest {
        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData)
        assertTrue(id > 0)
    }

    @Test
    fun `getNoDeletePasteData retrieves created paste`() = runTest {
        val pasteData = createTestPasteData(text = "test retrieval")
        val id = pasteDao.createPasteData(pasteData)

        val retrieved = pasteDao.getNoDeletePasteData(id)
        assertNotNull(retrieved)
        assertEquals(id, retrieved.id)
        assertEquals(pasteData.appInstanceId, retrieved.appInstanceId)
        assertEquals(pasteData.pasteType, retrieved.pasteType)
        assertEquals(pasteData.hash, retrieved.hash)
    }

    @Test
    fun `getNoDeletePasteData returns null for non-existent id`() = runTest {
        assertNull(pasteDao.getNoDeletePasteData(99999L))
    }

    @Test
    fun `getLoadedPasteDataBlock retrieves LOADED paste`() = runTest {
        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData)

        val retrieved = pasteDao.getLoadedPasteDataBlock(id)
        assertNotNull(retrieved)
        assertEquals(PasteState.LOADED, retrieved.pasteState)
    }

    @Test
    fun `getLoadingPasteData returns null for LOADED paste`() = runTest {
        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData)

        assertNull(pasteDao.getLoadingPasteData(id))
    }

    @Test
    fun `getLoadingPasteData retrieves LOADING paste`() = runTest {
        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData, pasteState = PasteState.LOADING)

        val retrieved = pasteDao.getLoadingPasteData(id)
        assertNotNull(retrieved)
        assertEquals(PasteState.LOADING, retrieved.pasteState)
    }

    @Test
    fun `multiple createPasteData calls return unique ids`() = runTest {
        val id1 = pasteDao.createPasteData(createTestPasteData(text = "first"))
        val id2 = pasteDao.createPasteData(createTestPasteData(text = "second"))
        val id3 = pasteDao.createPasteData(createTestPasteData(text = "third"))

        assertTrue(id1 != id2)
        assertTrue(id2 != id3)
    }

    // --- Favorite ---

    @Test
    fun `setFavorite true marks paste as favorite`() = runTest {
        val pasteData = createTestPasteData(favorite = false)
        val id = pasteDao.createPasteData(pasteData)

        pasteDao.setFavorite(id, true)
        val retrieved = pasteDao.getNoDeletePasteData(id)
        assertNotNull(retrieved)
        assertTrue(retrieved.favorite)
    }

    @Test
    fun `setFavorite false removes favorite`() = runTest {
        val pasteData = createTestPasteData(favorite = true)
        val id = pasteDao.createPasteData(pasteData)

        pasteDao.setFavorite(id, false)
        val retrieved = pasteDao.getNoDeletePasteData(id)
        assertNotNull(retrieved)
        assertFalse(retrieved.favorite)
    }

    // --- Update state ---

    @Test
    fun `updatePasteState changes paste state`() = runTest {
        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData, pasteState = PasteState.LOADING)

        pasteDao.updatePasteState(id, PasteState.LOADED)
        val retrieved = pasteDao.getNoDeletePasteData(id)
        assertNotNull(retrieved)
        assertEquals(PasteState.LOADED, retrieved.pasteState)
    }

    @Test
    fun `updateCreateTime changes the timestamp`() = runTest {
        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData)
        val before = pasteDao.getNoDeletePasteData(id)!!.createTime

        Thread.sleep(10)
        pasteDao.updateCreateTime(id)

        val after = pasteDao.getNoDeletePasteData(id)!!.createTime
        assertTrue(after >= before)
    }

    // --- Delete ---

    @Test
    fun `markDeletePasteData marks paste as deleted`() = runTest {
        coEvery { taskSubmitter.submit(any()) } coAnswers {
            val block = firstArg<suspend com.crosspaste.task.TaskBuilder.() -> Unit>()
            val builder = mockk<com.crosspaste.task.TaskBuilder>(relaxed = true)
            every { builder.addDeletePasteTasks(any()) } returns builder
            block.invoke(builder)
        }

        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData)
        assertNotNull(pasteDao.getNoDeletePasteData(id))

        pasteDao.markDeletePasteData(id)

        // getNoDeletePasteData should return null because it filters out DELETED
        assertNull(pasteDao.getNoDeletePasteData(id))
    }

    @Test
    fun `getDeletePasteData retrieves marked-deleted paste`() = runTest {
        coEvery { taskSubmitter.submit(any()) } coAnswers {
            val block = firstArg<suspend com.crosspaste.task.TaskBuilder.() -> Unit>()
            val builder = mockk<com.crosspaste.task.TaskBuilder>(relaxed = true)
            every { builder.addDeletePasteTasks(any()) } returns builder
            block.invoke(builder)
        }

        val pasteData = createTestPasteData()
        val id = pasteDao.createPasteData(pasteData)

        pasteDao.markDeletePasteData(id)

        val deleted = pasteDao.getDeletePasteData(id)
        assertNotNull(deleted)
        assertEquals(PasteState.DELETED, deleted.pasteState)
    }

    // --- Search ---

    @Test
    fun `searchPasteData returns all with empty search terms`() = runTest {
        pasteDao.createPasteData(createTestPasteData(text = "apple"))
        pasteDao.createPasteData(createTestPasteData(text = "banana"))
        pasteDao.createPasteData(createTestPasteData(text = "cherry"))

        val results = pasteDao.searchPasteData(
            searchTerms = listOf(),
            limit = 100,
        )
        assertEquals(3, results.size)
    }

    @Test
    fun `searchPasteData with limit returns limited results`() = runTest {
        pasteDao.createPasteData(createTestPasteData(text = "item1"))
        pasteDao.createPasteData(createTestPasteData(text = "item2"))
        pasteDao.createPasteData(createTestPasteData(text = "item3"))

        val results = pasteDao.searchPasteData(
            searchTerms = listOf(),
            limit = 2,
        )
        assertEquals(2, results.size)
    }

    @Test
    fun `searchPasteData with favorite filter`() = runTest {
        val id1 = pasteDao.createPasteData(createTestPasteData(text = "fav item"))
        pasteDao.createPasteData(createTestPasteData(text = "normal item"))
        pasteDao.setFavorite(id1, true)

        val results = pasteDao.searchPasteData(
            searchTerms = listOf(),
            favorite = true,
            limit = 100,
        )
        assertEquals(1, results.size)
        assertTrue(results[0].favorite)
    }

    @Test
    fun `searchPasteData with type filter`() = runTest {
        pasteDao.createPasteData(createTestPasteData(text = "text item"))

        val urlItem = createUrlPasteItem(url = "https://example.com")
        val urlPaste = PasteData(
            appInstanceId = "test-instance",
            pasteAppearItem = urlItem,
            pasteCollection = PasteCollection(listOf()),
            pasteType = PasteType.URL_TYPE.type,
            size = urlItem.size,
            hash = urlItem.hash,
            pasteState = PasteState.LOADED,
            createTime = DateUtils.nowEpochMilliseconds(),
        )
        pasteDao.createPasteData(urlPaste)

        val textResults = pasteDao.searchPasteData(
            searchTerms = listOf(),
            pasteType = PasteType.TEXT_TYPE.type,
            limit = 100,
        )
        assertEquals(1, textResults.size)
        assertEquals(PasteType.TEXT_TYPE.type, textResults[0].pasteType)
    }

    // --- Size queries ---

    @Test
    fun `getSize returns total size of all pastes`() = runTest {
        val paste1 = createTestPasteData(text = "hello")
        val paste2 = createTestPasteData(text = "world")
        pasteDao.createPasteData(paste1)
        pasteDao.createPasteData(paste2)

        val totalSize = pasteDao.getSize(allOrFavorite = true)
        assertEquals(paste1.size + paste2.size, totalSize)
    }

    @Test
    fun `getSize with allOrFavorite false returns only favorite size`() = runTest {
        val paste1 = createTestPasteData(text = "hello")
        val paste2 = createTestPasteData(text = "world")
        val id1 = pasteDao.createPasteData(paste1)
        pasteDao.createPasteData(paste2)
        pasteDao.setFavorite(id1, true)

        val favoriteSize = pasteDao.getSize(allOrFavorite = false)
        assertEquals(paste1.size, favoriteSize)
    }

    @Test
    fun `getSize returns 0 for empty database`() = runTest {
        assertEquals(0L, pasteDao.getSize(allOrFavorite = true))
    }

    @Test
    fun `getMinPasteDataCreateTime returns earliest time`() = runTest {
        val earlyPaste = createTestPasteData(text = "early").copy(createTime = 1000L)
        val latePaste = createTestPasteData(text = "late").copy(createTime = 2000L)
        pasteDao.createPasteData(earlyPaste)
        pasteDao.createPasteData(latePaste)

        val minTime = pasteDao.getMinPasteDataCreateTime()
        assertEquals(1000L, minTime)
    }

    @Test
    fun `getMinPasteDataCreateTime returns null for empty database`() = runTest {
        assertNull(pasteDao.getMinPasteDataCreateTime())
    }

    @Test
    fun `getSizeByTimeLessThan returns cumulative size before given time`() = runTest {
        val paste1 = createTestPasteData(text = "old").copy(createTime = 1000L)
        val paste2 = createTestPasteData(text = "new").copy(createTime = 5000L)
        pasteDao.createPasteData(paste1)
        pasteDao.createPasteData(paste2)

        val sizeBefore3000 = pasteDao.getSizeByTimeLessThan(3000L)
        assertEquals(paste1.size, sizeBefore3000)
    }

    // --- Tags ---

    @Test
    fun `createPasteTag returns valid id`() = runTest {
        val tagId = pasteDao.createPasteTag("important", 0xFF0000L)
        assertTrue(tagId > 0)
    }

    @Test
    fun `updatePasteTagName changes tag name`() = runTest {
        val tagId = pasteDao.createPasteTag("old_name", 0xFF0000L)
        pasteDao.updatePasteTagName(tagId, "new_name")
        // Verify through flow
        pasteDao.getAllTagsFlow().test {
            val tags = awaitItem()
            val tag = tags.find { it.id == tagId }
            assertNotNull(tag)
            assertEquals("new_name", tag.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updatePasteTagColor changes tag color`() = runTest {
        val tagId = pasteDao.createPasteTag("tag", 0xFF0000L)
        pasteDao.updatePasteTagColor(tagId, 0x00FF00L)
        pasteDao.getAllTagsFlow().test {
            val tags = awaitItem()
            val tag = tags.find { it.id == tagId }
            assertNotNull(tag)
            assertEquals(0x00FF00L, tag.color)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switchPinPasteTagBlock toggles pin state`() = runTest {
        val pasteData = createTestPasteData()
        val pasteId = pasteDao.createPasteData(pasteData)
        val tagId = pasteDao.createPasteTag("tag1", 0xFF0000L)

        // Initially not pinned
        var pinned = pasteDao.getPasteTagsBlock(pasteId)
        assertFalse(pinned.contains(tagId))

        // Pin
        pasteDao.switchPinPasteTagBlock(pasteId, tagId)
        pinned = pasteDao.getPasteTagsBlock(pasteId)
        assertTrue(pinned.contains(tagId))

        // Unpin
        pasteDao.switchPinPasteTagBlock(pasteId, tagId)
        pinned = pasteDao.getPasteTagsBlock(pasteId)
        assertFalse(pinned.contains(tagId))
    }

    @Test
    fun `deletePasteTagBlock removes tag`() = runTest {
        val tagId = pasteDao.createPasteTag("to_delete", 0xFF0000L)
        pasteDao.deletePasteTagBlock(tagId)

        pasteDao.getAllTagsFlow().test {
            val tags = awaitItem()
            assertNull(tags.find { it.id == tagId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMaxSortOrder returns max sort order`() = runTest {
        val initialMax = pasteDao.getMaxSortOrder()
        pasteDao.createPasteTag("tag1", 0xFF0000L)
        val afterFirst = pasteDao.getMaxSortOrder()
        pasteDao.createPasteTag("tag2", 0x00FF00L)
        val afterSecond = pasteDao.getMaxSortOrder()

        assertTrue(afterFirst > initialMax)
        assertTrue(afterSecond > afterFirst)
    }

    // --- Flow ---

    @Test
    fun `getAllTagsFlow emits on tag creation`() = runTest {
        pasteDao.getAllTagsFlow().test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())

            pasteDao.createPasteTag("new_tag", 0xFF0000L)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("new_tag", updated[0].name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Batch read ---

    @Test
    fun `batchReadPasteData processes all pastes`() = runTest {
        pasteDao.createPasteData(createTestPasteData(text = "a"))
        pasteDao.createPasteData(createTestPasteData(text = "b"))
        pasteDao.createPasteData(createTestPasteData(text = "c"))

        val processed = mutableListOf<PasteData>()
        val count = pasteDao.batchReadPasteData(
            batchNum = 2,
            readPasteDataList = { id, limit ->
                database.pasteDatabaseQueries.getBatchPasteData(id, limit, PasteData::mapper)
                    .executeAsList()
            },
            dealPasteData = { processed.add(it) },
        )

        assertEquals(3L, count)
        assertEquals(3, processed.size)
    }

    @Test
    fun `batchReadPasteData returns 0 for empty database`() = runTest {
        val count = pasteDao.batchReadPasteData(
            readPasteDataList = { id, limit ->
                database.pasteDatabaseQueries.getBatchPasteData(id, limit, PasteData::mapper)
                    .executeAsList()
            },
            dealPasteData = {},
        )

        assertEquals(0L, count)
    }

    // --- Search by source ---

    @Test
    fun `searchBySource returns matching pastes`() = runTest {
        pasteDao.createPasteData(createTestPasteData(text = "from chrome", source = "Chrome"))
        pasteDao.createPasteData(createTestPasteData(text = "from vscode", source = "VSCode"))
        pasteDao.createPasteData(createTestPasteData(text = "also chrome", source = "Chrome"))

        val results = pasteDao.searchBySource("Chrome")
        assertEquals(2, results.size)
    }

    @Test
    fun `searchBySource returns empty for no match`() = runTest {
        pasteDao.createPasteData(createTestPasteData(text = "test", source = "Chrome"))
        val results = pasteDao.searchBySource("Firefox")
        assertTrue(results.isEmpty())
    }

    // --- Update paste appear item ---

    @Test
    fun `updatePasteAppearItem changes the item and hash`() = runTest {
        val pasteData = createTestPasteData(text = "original")
        val id = pasteDao.createPasteData(pasteData)

        val newItem = createTextPasteItem(
            identifiers = listOf(DesktopTextTypePlugin.TEXT),
            text = "updated",
        )
        val result = pasteDao.updatePasteAppearItem(
            id = id,
            pasteItem = newItem,
            pasteSearchContent = "updated",
        )
        assertTrue(result.isSuccess)

        val retrieved = pasteDao.getNoDeletePasteData(id)
        assertNotNull(retrieved)
        assertEquals(newItem.hash, retrieved.hash)
    }
}
