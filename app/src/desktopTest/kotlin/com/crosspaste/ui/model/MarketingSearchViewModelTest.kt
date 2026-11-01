package com.crosspaste.ui.model

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MarketingSearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MarketingPasteSearchViewModel =
        // relaxed: the view model only reads the (static) sample paste list in its initializer.
        MarketingPasteSearchViewModel(mockk<MarketingPasteData>(relaxed = true))

    @Test
    fun `convertTerm lowercases splits and deduplicates the query`() {
        val viewModel = createViewModel()

        assertEquals(
            listOf("hello", "world"),
            viewModel.convertTerm("  Hello\tWORLD\nhello  "),
        )
    }

    @Test
    fun `convertTerm drops blank input`() {
        val viewModel = createViewModel()

        assertEquals(emptyList(), viewModel.convertTerm(""))
        assertEquals(emptyList(), viewModel.convertTerm("  \t\n "))
    }
}
