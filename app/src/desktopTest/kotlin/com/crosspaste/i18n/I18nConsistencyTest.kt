package com.crosspaste.i18n

import com.crosspaste.i18n.SupportedLanguages.EN
import com.crosspaste.i18n.SupportedLanguages.LANGUAGE_LIST
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the i18n property files against the mistakes that [DesktopCopywriter]
 * would otherwise swallow at runtime: a key present in one locale but not another
 * silently falls back to English, a duplicate key silently keeps the last value,
 * an empty value renders as nothing, and a stray `%` throws from `String.format`
 * the first time the text is shown.
 *
 * Every check reads the raw file lines rather than a loaded [java.util.Properties],
 * because `Properties` is exactly what hides duplicates and ordering.
 *
 * Unused keys are reported to stdout but never fail the build: a key may be used
 * by the mobile apps that share `commonMain`, or built dynamically (see
 * [DYNAMIC_KEY_PREFIXES]). Confirm both before deleting one.
 */
class I18nConsistencyTest {

    private data class Entry(
        val key: String,
        val value: String,
        val line: Int,
    )

    private data class LocaleFile(
        val language: String,
        val entries: List<Entry>,
    ) {
        val keys: List<String> get() = entries.map { it.key }
        val byKey: Map<String, String> get() = entries.associate { it.key to it.value }
    }

    companion object {
        private val KEY_PATTERN = Regex("^[a-z0-9_?]+$")

        /** Keys built at runtime by prefix + suffix; no literal reference exists. */
        private val DYNAMIC_KEY_PREFIXES = listOf("desktop_guide_", "relative_")

        /** Keys referenced only by the mobile apps, which reuse `commonMain`. */
        private val MOBILE_ONLY_KEYS = emptySet<String>()

        // Literals with `$` are interpolated at runtime (e.g. "$guideKey$index"); see DYNAMIC_KEY_PREFIXES.
        private val GET_TEXT_LITERAL = Regex("""getText\(\s*"([^"$]+)"""")
        private val ANY_LITERAL = Regex(""""([a-z][a-z0-9_?]*)"""")

        // Matches every conversion java.util.Formatter accepts, including %% and %n.
        private val FORMAT_SPECIFIER = Regex("""%(\d+\$)?[-#+ 0,(<]*\d*(\.\d+)?([tT][a-zA-Z]|[a-zA-Z%])""")
    }

    private val moduleDir: Path = findModuleDir()

    private val i18nDir: Path = moduleDir.resolve("src/desktopMain/resources/i18n")

    private val sourceRoots: List<Path> =
        listOf(
            moduleDir.resolve("src/commonMain/kotlin"),
            moduleDir.resolve("src/desktopMain/kotlin"),
        )

    private val locales: Map<String, LocaleFile> by lazy {
        LANGUAGE_LIST.associateWith { parse(it) }
    }

    private fun findModuleDir(): Path {
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("src/desktopMain/resources/i18n"))) return dir
            val app = dir.resolve("app")
            if (Files.isDirectory(app.resolve("src/desktopMain/resources/i18n"))) return app
            dir = dir.parent
        }
        fail("Cannot locate the app module from ${Paths.get("").toAbsolutePath()}")
    }

    private fun parse(language: String): LocaleFile {
        val file = i18nDir.resolve("$language.properties")
        assertTrue(file.isRegularFile(), "Missing $file")
        val entries =
            file.readText().lines().mapIndexedNotNull { index, raw ->
                val line = raw.trimEnd()
                if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) return@mapIndexedNotNull null
                val separator = line.indexOf('=')
                assertTrue(separator > 0, "$language.properties:${index + 1} is not key=value: $line")
                Entry(line.substring(0, separator), line.substring(separator + 1), index + 1)
            }
        return LocaleFile(language, entries)
    }

    private fun formatArity(value: String): Int =
        FORMAT_SPECIFIER.findAll(value).count {
            it.groupValues[3] !in
                setOf("%", "n")
        }

    @Test
    fun `every locale has the same key set as English`() {
        val en = locales.getValue(EN).keys.toSet()
        val problems =
            locales.values
                .filter { it.language != EN }
                .flatMap { locale ->
                    val keys = locale.keys.toSet()
                    (en - keys).map { "${locale.language}: missing $it" } +
                        (keys - en).map { "${locale.language}: extra $it (not in en)" }
                }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `no locale defines a key twice`() {
        val problems =
            locales.values.flatMap { locale ->
                locale.entries
                    .groupBy { it.key }
                    .filter { it.value.size > 1 }
                    .map { (key, dups) -> "${locale.language}: $key defined at lines ${dups.map { it.line }}" }
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `no locale has an empty value or a malformed key`() {
        val problems =
            locales.values.flatMap { locale ->
                locale.entries.mapNotNull { entry ->
                    when {
                        entry.value.isBlank() -> "${locale.language}:${entry.line} ${entry.key} has an empty value"
                        !KEY_PATTERN.matches(
                            entry.key,
                        ) -> "${locale.language}:${entry.line} malformed key '${entry.key}'"
                        else -> null
                    }
                }
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `every locale is sorted by key`() {
        val problems =
            locales.values.mapNotNull { locale ->
                val keys = locale.keys
                val sorted = keys.sorted()
                val firstOutOfOrder = keys.indices.firstOrNull { keys[it] != sorted[it] }
                firstOutOfOrder?.let {
                    "${locale.language}.properties:${locale.entries[it].line} '${keys[it]}' is out of order (expected '${sorted[it]}')"
                }
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `every value formats without throwing and locales agree on argument count`() {
        // DesktopCopywriter always calls value.format(*args), even with no args, so a
        // stray '%' in any locale throws the first time that text is displayed.
        val enArity = locales.getValue(EN).byKey.mapValues { formatArity(it.value) }
        val problems =
            locales.values.flatMap { locale ->
                locale.entries.mapNotNull { entry ->
                    val arity = formatArity(entry.value)
                    val args = Array<Any?>(arity) { "x" }
                    val formatError = runCatching { entry.value.format(*args) }.exceptionOrNull()
                    when {
                        formatError != null ->
                            "${locale.language}:${entry.line} ${entry.key} does not format: ${formatError.message}"
                        enArity[entry.key]?.let { it != arity } == true ->
                            "${locale.language}:${entry.line} ${entry.key} expects $arity args, en expects ${enArity[entry.key]}"
                        else -> null
                    }
                }
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `every key referenced by getText in source exists in English`() {
        val en = locales.getValue(EN).keys.toSet()
        val problems =
            kotlinSources()
                .flatMap { file ->
                    GET_TEXT_LITERAL
                        .findAll(file.readText())
                        .map { it.groupValues[1] }
                        .filter { it !in en }
                        .map { "${moduleDir.relativize(file)}: getText(\"$it\") has no entry in en.properties" }
                        .toList()
                }.toList()
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `report keys with no literal reference in desktop or common source`() {
        // Report only. A key here may be used by mobile or built dynamically; verify
        // both before deleting it, then update MOBILE_ONLY_KEYS / DYNAMIC_KEY_PREFIXES.
        val referenced =
            kotlinSources()
                .flatMap { file -> ANY_LITERAL.findAll(file.readText()).map { it.groupValues[1] }.toList() }
                .toSet()
        val unreferenced =
            locales
                .getValue(EN)
                .keys
                .filter { key -> key !in referenced }
                .filterNot { key -> DYNAMIC_KEY_PREFIXES.any { key.startsWith(it) } }
                .filterNot { it in MOBILE_ONLY_KEYS }
        if (unreferenced.isNotEmpty()) {
            println("i18n keys with no literal reference in commonMain/desktopMain (${unreferenced.size}):")
            unreferenced.forEach { println("  $it") }
        }
    }

    private fun kotlinSources(): Sequence<Path> =
        sourceRoots.asSequence().flatMap { root ->
            Files.walk(root).asSequence().filter {
                it.isRegularFile() &&
                    it.extension == "kt" &&
                    it.name != "I18nConsistencyTest.kt"
            }
        }
}
