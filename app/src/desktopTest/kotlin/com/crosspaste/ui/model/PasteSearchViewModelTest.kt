package com.crosspaste.ui.model

import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PasteSearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /** Minimal concrete subclass for testing base class logic. */
    private class TestSearchViewModel : PasteSearchViewModel() {
        override val convertTerm: (String) -> List<String> = { input ->
            input
                .trim()
                .lowercase()
                .split("\\s+".toRegex())
                .filterNot { it.isEmpty() }
                .distinct()
        }
        override val tagList: StateFlow<List<PasteTag>> = MutableStateFlow(listOf())
        override val searchResults: StateFlow<List<PasteData>> = MutableStateFlow(listOf())
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial search base params have default values`() {
        val vm = TestSearchViewModel()
        val params = vm.searchBaseParams.value
        assertTrue(params.pasteTypeList.isEmpty())
        assertTrue(params.sort)
        assertNull(params.tag)
        assertEquals(PasteSearchViewModel.QUERY_BATCH_SIZE, params.limit)
    }

    @Test
    fun `updateInputSearch sets input and resets limit`() {
        val vm = TestSearchViewModel()
        vm.updateInputSearch("hello")
        assertEquals("hello", vm.inputSearch.value)
        assertEquals(PasteSearchViewModel.QUERY_BATCH_SIZE, vm.searchBaseParams.value.limit)
    }

    @Test
    fun `switchSort toggles sort flag`() {
        val vm = TestSearchViewModel()
        assertTrue(vm.searchBaseParams.value.sort)

        vm.switchSort()
        assertFalse(vm.searchBaseParams.value.sort)

        vm.switchSort()
        assertTrue(vm.searchBaseParams.value.sort)
    }

    @Test
    fun `switchSort does not reset limit`() {
        val vm = TestSearchViewModel()
        // switchSort should not change the limit (unlike other operations)
        val limitBefore = vm.searchBaseParams.value.limit
        vm.switchSort()
        assertEquals(limitBefore, vm.searchBaseParams.value.limit)
    }

    @Test
    fun `updatePasteType sets paste type and resets limit`() {
        val vm = TestSearchViewModel()
        vm.updatePasteType(listOf(3))
        assertEquals(listOf(3), vm.searchBaseParams.value.pasteTypeList)
        assertEquals(PasteSearchViewModel.QUERY_BATCH_SIZE, vm.searchBaseParams.value.limit)
    }

    @Test
    fun `updatePasteType with empty list clears filter`() {
        val vm = TestSearchViewModel()
        vm.updatePasteType(listOf(3))
        assertEquals(listOf(3), vm.searchBaseParams.value.pasteTypeList)

        vm.updatePasteType(listOf())
        assertTrue(
            vm.searchBaseParams.value.pasteTypeList
                .isEmpty(),
        )
    }

    @Test
    fun `updateTag toggles tag selection`() {
        val vm = TestSearchViewModel()
        // First call sets the tag
        vm.updateTag(42L)
        assertEquals(42L, vm.searchBaseParams.value.tag)

        // Same tag again clears it
        vm.updateTag(42L)
        assertNull(vm.searchBaseParams.value.tag)
    }

    @Test
    fun `updateTag with different tag replaces previous`() {
        val vm = TestSearchViewModel()
        vm.updateTag(1L)
        assertEquals(1L, vm.searchBaseParams.value.tag)

        vm.updateTag(2L)
        assertEquals(2L, vm.searchBaseParams.value.tag)
    }

    @Test
    fun `checkLoadAll sets loadAll when size below limit`() {
        val vm = TestSearchViewModel()
        // limit defaults to QUERY_BATCH_SIZE (50)
        vm.checkLoadAll(30) // 30 < 50 → all loaded
        assertTrue(vm.loadAll.value)
    }

    @Test
    fun `checkLoadAll does not set loadAll when size equals limit`() {
        val vm = TestSearchViewModel()
        vm.checkLoadAll(PasteSearchViewModel.QUERY_BATCH_SIZE) // exactly 50
        // loadAll should NOT be set because size >= limit means more data may exist
        assertFalse(vm.loadAll.value)
    }

    @Test
    fun `resetSearch clears all parameters to defaults`() {
        val vm = TestSearchViewModel()

        // Change everything
        vm.updateInputSearch("test")
        vm.switchSort()
        vm.updatePasteType(listOf(2))
        vm.updateTag(99L)

        vm.resetSearch()

        assertEquals("", vm.inputSearch.value)
        val params = vm.searchBaseParams.value
        assertTrue(params.pasteTypeList.isEmpty())
        assertTrue(params.sort)
        assertEquals(PasteSearchViewModel.QUERY_BATCH_SIZE, params.limit)
    }

    @Test
    fun `resetSearch clears tag`() {
        val vm = TestSearchViewModel()
        vm.updateTag(5L)
        vm.resetSearch()
        // resetSearch resets pasteType, sort, tag, and limit
        assertNull(vm.searchBaseParams.value.tag)
    }

    @Test
    fun `tryAddLimit is no-op when loadAll is true`() {
        val vm = TestSearchViewModel()
        vm.checkLoadAll(10) // sets loadAll = true
        val limitBefore = vm.searchBaseParams.value.limit
        vm.tryAddLimit()
        // limit should not change when loadAll is true
        assertEquals(limitBefore, vm.searchBaseParams.value.limit)
    }

    @Test
    fun `QUERY_BATCH_SIZE is 50`() {
        assertEquals(50, PasteSearchViewModel.QUERY_BATCH_SIZE)
    }

    @Test
    fun `LOAD_MORE_THROTTLE_MS is 500`() {
        assertEquals(500L, PasteSearchViewModel.LOAD_MORE_THROTTLE_MS)
    }
}
