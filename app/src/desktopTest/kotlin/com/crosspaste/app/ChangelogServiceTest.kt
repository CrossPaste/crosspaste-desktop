package com.crosspaste.app

import com.crosspaste.i18n.GlobalCopywriter
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogServiceTest {

    private fun serviceForLanguage(language: String): ChangelogService {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.language() } returns language
        return ChangelogService(copywriter)
    }

    @Test
    fun `parses bundled english changelog into version sections`() {
        val entries = serviceForLanguage("en").loadEntries()

        assertTrue(entries.isNotEmpty(), "English changelog should parse at least one entry")

        val first = entries.first()
        assertEquals("2.1.3", first.version)
        assertEquals("2026-05-25", first.date)
        assertTrue(first.body.isNotEmpty(), "Entry body should not be empty")
        assertTrue(
            first.body.any { it.startsWith("## ") },
            "Entry body should contain subheadings",
        )
    }

    @Test
    fun `chinese language loads the chinese changelog`() {
        val entries = serviceForLanguage("zh").loadEntries()

        assertTrue(entries.isNotEmpty())
        assertTrue(
            entries.first().body.any { it.contains("更快") },
            "Chinese changelog body should contain Chinese text",
        )
    }

    @Test
    fun `unknown language falls back to english changelog`() {
        val entries = serviceForLanguage("fr").loadEntries()

        assertTrue(entries.isNotEmpty())
        assertEquals("2.1.3", entries.first().version)
    }

    @Test
    fun `entries preserve declared order`() {
        val versions = serviceForLanguage("en").loadEntries().map { it.version }
        assertEquals(listOf("2.1.3", "2.0.0"), versions)
    }
}
