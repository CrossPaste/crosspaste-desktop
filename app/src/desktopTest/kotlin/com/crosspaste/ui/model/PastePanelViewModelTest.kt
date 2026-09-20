package com.crosspaste.ui.model

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.db.paste.SearchPasteData
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class PastePanelViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeSearchPasteData(
        val results: MutableStateFlow<List<PasteData>>,
    ) : SearchPasteData {
        var lastLimit: Int = 0

        override suspend fun searchPasteData(
            searchTerms: List<String>,
            local: Boolean?,
            pasteTypeList: List<Int>,
            sort: Boolean,
            tag: Long?,
            limit: Int,
        ): List<PasteData> = results.value

        override fun searchPasteDataFlow(
            searchTerms: List<String>,
            local: Boolean?,
            pasteTypeList: List<Int>,
            sort: Boolean,
            tag: Long?,
            limit: Int,
        ): Flow<List<PasteData>> {
            lastLimit = limit
            return results
        }

        override suspend fun searchBySource(source: String): List<PasteData> = listOf()
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

    private val windowManager = mockk<DesktopAppWindowManager>(relaxed = true)
    private val pasteboardService =
        mockk<PasteboardService> {
            // The primary-type default argument reads the config through the service
            every { configManager } returns
                mockk {
                    every { getCurrentConfig() } returns DesktopAppConfig(language = "en")
                }
            coEvery {
                tryWritePasteboard(any<PasteData>(), any(), any(), any())
            } returns Result.success(null)
        }

    // Newest first: 3 was copied last, 1 first
    private val history = MutableStateFlow(listOf(pasteData(3), pasteData(2), pasteData(1)))
    private val searchPasteData = FakeSearchPasteData(history)

    private fun createViewModel() = PastePanelViewModel(windowManager, pasteboardService, searchPasteData)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pasting a row writes it, simulates paste and advances to the row copied after it`() =
        runTest {
            val vm = createViewModel()
            val scrolls = mutableListOf<Int>()
            val jobs =
                listOf(
                    launch { vm.items.collect {} },
                    launch { vm.nextIndex.collect {} },
                    launch { vm.scrollToIndex.collect { scrolls.add(it) } },
                )
            advanceUntilIdle()
            assertEquals(0, vm.nextIndex.value)

            vm.paste(pasteData(1))
            advanceUntilIdle()

            coVerify(exactly = 1) {
                pasteboardService.tryWritePasteboard(
                    pasteData = match { it.id == 1L },
                    localOnly = true,
                    primary = any(),
                    updateCreateTime = false,
                )
            }
            coVerify(exactly = 1) { windowManager.toPaste() }
            assertEquals(1, vm.nextIndex.value)
            assertEquals(listOf(1), scrolls)

            vm.paste(pasteData(2))
            advanceUntilIdle()
            assertEquals(0, vm.nextIndex.value)
            assertEquals(listOf(1, 0), scrolls)

            jobs.forEach { it.cancel() }
        }

    @Test
    fun `pasting the newest row keeps the marker on it`() =
        runTest {
            val vm = createViewModel()
            val scrolls = mutableListOf<Int>()
            val jobs =
                listOf(
                    launch { vm.items.collect {} },
                    launch { vm.nextIndex.collect {} },
                    launch { vm.scrollToIndex.collect { scrolls.add(it) } },
                )
            advanceUntilIdle()

            vm.paste(pasteData(3))
            advanceUntilIdle()

            assertEquals(0, vm.nextIndex.value)
            assertEquals(emptyList(), scrolls)

            jobs.forEach { it.cancel() }
        }

    @Test
    fun `marker follows the row by id when a new copy arrives on top`() =
        runTest {
            val vm = createViewModel()
            val jobs =
                listOf(
                    launch { vm.items.collect {} },
                    launch { vm.nextIndex.collect {} },
                )
            advanceUntilIdle()

            vm.paste(pasteData(1))
            advanceUntilIdle()
            assertEquals(1, vm.nextIndex.value)

            history.value = listOf(pasteData(4)) + history.value
            advanceUntilIdle()
            assertEquals(2, vm.nextIndex.value)

            jobs.forEach { it.cancel() }
        }

    @Test
    fun `a failed clipboard write neither pastes nor advances`() =
        runTest {
            coEvery {
                pasteboardService.tryWritePasteboard(any<PasteData>(), any(), any(), any())
            } returns Result.failure(IllegalStateException("boom"))
            val vm = createViewModel()
            val jobs =
                listOf(
                    launch { vm.items.collect {} },
                    launch { vm.nextIndex.collect {} },
                )
            advanceUntilIdle()

            vm.paste(pasteData(1))
            advanceUntilIdle()

            coVerify(exactly = 0) { windowManager.toPaste() }
            assertEquals(0, vm.nextIndex.value)

            jobs.forEach { it.cancel() }
        }

    @Test
    fun `reopening resets the marker and the page size`() =
        runTest {
            val vm = createViewModel()
            val jobs =
                listOf(
                    launch { vm.items.collect {} },
                    launch { vm.nextIndex.collect {} },
                )
            advanceUntilIdle()

            vm.paste(pasteData(1))
            advanceUntilIdle()
            assertEquals(1, vm.nextIndex.value)

            vm.onShown()
            advanceUntilIdle()
            assertEquals(0, vm.nextIndex.value)
            assertEquals(PastePanelViewModel.PAGE_SIZE, searchPasteData.lastLimit)

            jobs.forEach { it.cancel() }
        }

    @Test
    fun `loadMore grows the page until the query returns a short page`() =
        runTest {
            val vm = createViewModel()
            val jobs = listOf(launch { vm.items.collect {} })
            advanceUntilIdle()
            // Three rows for a page of 50: everything is already loaded
            assertEquals(true, vm.loadAll.value)

            vm.loadMore()
            advanceUntilIdle()
            assertEquals(PastePanelViewModel.PAGE_SIZE, searchPasteData.lastLimit)

            jobs.forEach { it.cancel() }
        }
}
