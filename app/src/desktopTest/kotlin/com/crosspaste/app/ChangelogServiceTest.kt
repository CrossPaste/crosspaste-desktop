package com.crosspaste.app

import com.crosspaste.i18n.GlobalCopywriter
import io.mockk.every
import io.mockk.mockk
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogServiceTest {

    private fun serviceForLanguage(language: String): ChangelogService {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.language() } returns language
        return ChangelogService(copywriter)
    }

    private fun readResource(name: String): String =
        Thread
            .currentThread()
            .contextClassLoader
            .getResourceAsStream(name)!!
            .use { it.readBytes().toString(Charsets.UTF_8) }

    // The version the app is currently shipping; the changelog's newest entry must match it.
    private val currentVersion: String =
        Properties()
            .apply { load(readResource("crosspaste-version.properties").byteInputStream()) }
            .getProperty("version")

    // Version headers as they literally appear in a bundled changelog, top to bottom.
    private fun declaredVersions(resource: String): List<String> =
        Regex("""^#\s*\[([^]]+)]\s*-\s*.+$""", RegexOption.MULTILINE)
            .findAll(readResource(resource))
            .map { it.groupValues[1].trim() }
            .toList()

    @Test
    fun `parses bundled english changelog into version sections`() {
        val entries = serviceForLanguage("en").loadEntries()

        assertTrue(entries.isNotEmpty(), "English changelog should parse at least one entry")

        val first = entries.first()
        assertEquals(currentVersion, first.version, "Newest changelog entry must match the shipping version")
        assertTrue(
            Regex("""\d{4}-\d{2}-\d{2}""").matches(first.date),
            "Entry date should be an ISO date, was '${first.date}'",
        )
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
        assertEquals(currentVersion, entries.first().version)
        assertTrue(
            entries.first().body.any { line -> line.any { it.code in 0x4E00..0x9FFF } },
            "Chinese changelog body should contain Chinese characters",
        )
    }

    @Test
    fun `unknown language falls back to english changelog`() {
        val fallback = serviceForLanguage("fr").loadEntries()
        val english = serviceForLanguage("en").loadEntries()

        assertTrue(fallback.isNotEmpty())
        assertEquals(english, fallback)
    }

    @Test
    fun `entries preserve declared order`() {
        val versions = serviceForLanguage("en").loadEntries().map { it.version }
        assertEquals(declaredVersions("whats-new/en.md"), versions)
    }
}
