package com.crosspaste.ui.model

import com.crosspaste.db.paste.QueryPasteTag
import com.crosspaste.db.paste.SearchPasteData
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralPasteSearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeSearchPasteData(
        private val results: List<PasteData>,
    ) : SearchPasteData {
        override suspend fun searchPasteData(
            searchTerms: List<String>,
            local: Boolean?,
            pasteTypeList: List<Int>,
            sort: Boolean,
            tag: Long?,
            limit: Int,
        ): List<PasteData> = results

        override fun searchPasteDataFlow(
            searchTerms: List<String>,
            local: Boolean?,
            pasteTypeList: List<Int>,
            sort: Boolean,
            tag: Long?,
            limit: Int,
        ): Flow<List<PasteData>> = flowOf(results)

        override suspend fun searchBySource(source: String): List<PasteData> = listOf()
    }

    private object FakeQueryPasteTag : QueryPasteTag {
        override fun getAllTagsFlow(): Flow<List<PasteTag>> = flowOf(listOf())
    }

    private object FakeSearchContentService : SearchContentService {
        override fun createSearchContent(
            source: String?,
            searchContentList: List<String>,
        ): String = searchContentList.joinToString(" ")

        override fun createSearchTerms(queryString: String): List<String> =
            queryString
                .trim()
                .split("\\s+".toRegex())
                .filterNot { it.isEmpty() }
    }

    private fun pasteData(id: Long) =
        PasteData(
            id = id,
            appInstanceId = "test-instance",
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            size = 1,
            hash = "hash-$id",
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `searchResults drops rows with duplicate ids`() =
        runTest {
            val vm =
                GeneralPasteSearchViewModel(
                    FakeSearchPasteData(listOf(pasteData(1), pasteData(1), pasteData(2))),
                    FakeQueryPasteTag,
                    FakeSearchContentService,
                )

            val job = launch { vm.searchResults.collect {} }
            advanceUntilIdle()

            assertEquals(listOf(1L, 2L), vm.searchResults.value.map { it.id })
            job.cancel()
        }

    @Test
    fun `searchResults passes through unique ids unchanged`() =
        runTest {
            val vm =
                GeneralPasteSearchViewModel(
                    FakeSearchPasteData(listOf(pasteData(3), pasteData(1), pasteData(2))),
                    FakeQueryPasteTag,
                    FakeSearchContentService,
                )

            val job = launch { vm.searchResults.collect {} }
            advanceUntilIdle()

            assertEquals(listOf(3L, 1L, 2L), vm.searchResults.value.map { it.id })
            job.cancel()
        }
}
