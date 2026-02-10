package com.crosspaste.paste

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSearchContentServiceTest {

    private val service = DesktopSearchContentService()

    // ========== createSearchContent ==========

    @Test
    fun `createSearchContent with null source and empty list returns empty`() {
        val result = service.createSearchContent(null, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createSearchContent with source only returns source`() {
        val result = service.createSearchContent("MyApp", emptyList())
        assertEquals("MyApp", result)
    }

    @Test
    fun `createSearchContent with single content string tokenizes words`() {
        val result = service.createSearchContent(null, listOf("hello world"))
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("world"))
    }

    @Test
    fun `createSearchContent with source and content combines tokens`() {
        val result = service.createSearchContent("Chrome", listOf("search term"))
        assertTrue(result.contains("Chrome"))
        assertTrue(result.contains("search"))
        assertTrue(result.contains("term"))
    }

    @Test
    fun `createSearchContent deduplicates tokens`() {
        val result = service.createSearchContent(null, listOf("hello hello hello"))
        val tokens = result.split(" ")
        assertEquals(1, tokens.count { it == "hello" })
    }

    @Test
    fun `createSearchContent filters empty tokens`() {
        val result = service.createSearchContent(null, listOf("hello"))
        val tokens = result.split(" ")
        assertTrue(tokens.none { it.isEmpty() })
    }

    @Test
    fun `createSearchContent with multiple content strings tokenizes all`() {
        val result = service.createSearchContent(null, listOf("hello world", "foo bar"))
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("world"))
        assertTrue(result.contains("foo"))
        assertTrue(result.contains("bar"))
    }

    @Test
    fun `createSearchContent single string overload works`() {
        val result = service.createSearchContent(null, "hello world")
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("world"))
    }

    // ========== createSearchTerms ==========

    @Test
    fun `createSearchTerms extracts words`() {
        val terms = service.createSearchTerms("hello world")
        assertTrue(terms.contains("hello"))
        assertTrue(terms.contains("world"))
    }

    @Test
    fun `createSearchTerms deduplicates words`() {
        val terms = service.createSearchTerms("hello hello world")
        assertEquals(1, terms.count { it == "hello" })
    }

    @Test
    fun `createSearchTerms filters non-alphanumeric tokens`() {
        val terms = service.createSearchTerms("hello, world!")
        // Punctuation-only tokens should be filtered
        assertTrue(terms.none { it == "," })
        assertTrue(terms.none { it == "!" })
    }

    @Test
    fun `createSearchTerms with empty string returns empty list`() {
        val terms = service.createSearchTerms("")
        assertTrue(terms.isEmpty())
    }

    @Test
    fun `createSearchTerms with CJK text tokenizes by character boundaries`() {
        val terms = service.createSearchTerms("hello 你好")
        assertTrue(terms.isNotEmpty())
        assertTrue(terms.contains("hello"))
    }

    @Test
    fun `createSearchTerms with numbers includes them`() {
        val terms = service.createSearchTerms("version 123")
        assertTrue(terms.contains("version"))
        assertTrue(terms.contains("123"))
    }
}
