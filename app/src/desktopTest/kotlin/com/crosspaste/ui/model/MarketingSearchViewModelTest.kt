package com.crosspaste.ui.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the convertTerm logic from MarketingPasteSearchViewModel which splits,
 * lowercases, deduplicates, and filters empty terms from search input.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketingSearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /**
     * Extracts the convertTerm logic directly since MarketingPasteSearchViewModel
     * requires a MarketingPasteData dependency. We test the same lambda logic.
     */
    private val convertTerm: (String) -> List<String> = { inputSearch ->
        inputSearch
            .trim()
            .lowercase()
            .split("\\s+".toRegex())
            .filterNot { it.isEmpty() }
            .distinct()
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
    fun `convertTerm splits on whitespace`() {
        val terms = convertTerm("hello world")
        assertEquals(listOf("hello", "world"), terms)
    }

    @Test
    fun `convertTerm lowercases input`() {
        val terms = convertTerm("Hello WORLD")
        assertEquals(listOf("hello", "world"), terms)
    }

    @Test
    fun `convertTerm trims leading and trailing whitespace`() {
        val terms = convertTerm("  hello  ")
        assertEquals(listOf("hello"), terms)
    }

    @Test
    fun `convertTerm removes empty strings from multiple spaces`() {
        val terms = convertTerm("hello    world")
        assertEquals(listOf("hello", "world"), terms)
    }

    @Test
    fun `convertTerm deduplicates terms`() {
        val terms = convertTerm("hello hello world hello")
        assertEquals(listOf("hello", "world"), terms)
    }

    @Test
    fun `convertTerm returns empty list for empty string`() {
        val terms = convertTerm("")
        assertTrue(terms.isEmpty())
    }

    @Test
    fun `convertTerm returns empty list for whitespace only`() {
        val terms = convertTerm("   ")
        assertTrue(terms.isEmpty())
    }

    @Test
    fun `convertTerm handles tabs and newlines`() {
        val terms = convertTerm("hello\tworld\nfoo")
        assertEquals(listOf("hello", "world", "foo"), terms)
    }

    @Test
    fun `convertTerm preserves order with first occurrence`() {
        val terms = convertTerm("banana apple banana cherry apple")
        assertEquals(listOf("banana", "apple", "cherry"), terms)
    }
}
