package com.crosspaste.app

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.utils.DesktopResourceUtils
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A single product-facing release note section, parsed from the bundled
 * `whats-new/{language}.md` files. [body] holds the raw markdown lines between
 * this version header and the next (leading/trailing blank lines stripped).
 */
data class ChangelogEntry(
    val version: String,
    val date: String,
    val body: List<String>,
)

/**
 * Loads and parses the user-friendly changelog shipped inside the app resources.
 * This is intentionally separate from the developer `CHANGELOG.md`: the bundled
 * files contain plain-language, product-level notes only.
 */
class ChangelogService(
    private val copywriter: GlobalCopywriter,
) {
    private val logger = KotlinLogging.logger {}

    // Matches a version header line such as: "# [2.1.3] - 2026-05-25"
    private val headerRegex = Regex("""^#\s*\[([^]]+)]\s*-\s*(.+)$""")

    fun loadEntries(): List<ChangelogEntry> {
        val resource = resourceFor(copywriter.language())
        val text =
            runCatching {
                DesktopResourceUtils.readResourceBytes(resource).toString(Charsets.UTF_8)
            }.getOrElse { e ->
                logger.warn(e) { "Failed to load changelog resource $resource" }
                return emptyList()
            }
        return parse(text)
    }

    private fun resourceFor(language: String): String =
        if (language.lowercase().startsWith("zh")) {
            "whats-new/zh.md"
        } else {
            "whats-new/en.md"
        }

    private fun parse(text: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var version: String? = null
        var date = ""
        var body = mutableListOf<String>()

        fun flush() {
            version?.let { entries.add(ChangelogEntry(it, date, body.trimBlankEdges())) }
        }

        text.lineSequence().forEach { line ->
            val match = headerRegex.find(line.trim())
            if (match != null) {
                flush()
                version = match.groupValues[1].trim()
                date = match.groupValues[2].trim()
                body = mutableListOf()
            } else if (version != null) {
                body.add(line)
            }
        }
        flush()
        return entries
    }

    private fun List<String>.trimBlankEdges(): List<String> = dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
}
