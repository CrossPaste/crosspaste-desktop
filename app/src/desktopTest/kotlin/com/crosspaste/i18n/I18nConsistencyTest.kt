package com.crosspaste.i18n

import com.crosspaste.i18n.SupportedLanguages.EN
import com.crosspaste.i18n.SupportedLanguages.LANGUAGE_LIST
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.extension
import kotlin.io.path.inputStream
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
 * Duplicate and ordering checks read the raw file lines, because a loaded
 * [Properties] is exactly what hides both. Every other check runs against the
 * same [Properties] load the app performs, so continuation lines and escapes are
 * interpreted the way they will be at runtime, and the two views are checked
 * against each other so a stray trailing backslash cannot swallow a key unseen.
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
        /** Raw `key=value` lines in file order, for duplicate and ordering checks. */
        val entries: List<Entry>,
        /** What the app actually sees after `Properties.load`. */
        val loaded: Map<String, String>,
    ) {
        val rawKeys: List<String> get() = entries.map { it.key }
        val lineOf: Map<String, Int> get() = entries.associate { it.key to it.line }
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
        // Groups: 1 = explicit index ("2$"), 2 = flags, 3 = conversion.
        private val FORMAT_SPECIFIER = Regex("""%(\d+\$)?([-#+ 0,(<]*)\d*(?:\.\d+)?([tT][a-zA-Z]|[a-zA-Z%])""")
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
        val properties = Properties()
        InputStreamReader(file.inputStream(), StandardCharsets.UTF_8).use { properties.load(it) }
        val loaded = properties.entries.associate { it.key.toString() to it.value.toString() }
        return LocaleFile(language, entries, loaded)
    }

    private data class Spec(
        val index: Int,
        val conversion: Char,
    )

    /**
     * The argument slots a value actually references, following java.util.Formatter:
     * `%2$s` names slot 2, `%<s` reuses the previous slot, and a plain `%s` takes the
     * next ordinary slot regardless of any explicit indices seen before it.
     */
    private fun specs(value: String): List<Spec> {
        var ordinary = 0
        var previous = 0
        return FORMAT_SPECIFIER
            .findAll(value)
            .mapNotNull { match ->
                val conversion = match.groupValues[3].last()
                if (conversion == '%' || conversion == 'n') return@mapNotNull null
                val explicit = match.groupValues[1].dropLast(1).toIntOrNull()
                val index =
                    when {
                        explicit != null -> explicit
                        '<' in match.groupValues[2] -> previous
                        else -> ++ordinary
                    }
                previous = index
                Spec(index, conversion)
            }.toList()
    }

    private fun argumentCount(value: String): Int = specs(value).maxOfOrNull { it.index } ?: 0

    private fun sampleArgs(value: String): Array<Any?> {
        val samples = arrayOfNulls<Any?>(argumentCount(value))
        specs(value).forEach { spec ->
            if (spec.index in 1..samples.size && samples[spec.index - 1] == null) {
                samples[spec.index - 1] =
                    when (spec.conversion.lowercaseChar()) {
                        'd', 'o', 'x' -> 1
                        'e', 'f', 'g', 'a' -> 1.0
                        'c' -> 'x'
                        else -> "x"
                    }
            }
        }
        return samples
    }

    @Test
    fun `every locale has the same key set as English`() {
        val en = locales.getValue(EN).loaded.keys
        val problems =
            locales.values
                .filter { it.language != EN }
                .flatMap { locale ->
                    val keys = locale.loaded.keys
                    (en - keys).map { "${locale.language}: missing $it" } +
                        (keys - en).map { "${locale.language}: extra $it (not in en)" }
                }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `raw lines and the loaded Properties agree on the key set`() {
        // A value ending in a backslash makes Properties treat the next line as a
        // continuation, so that line's key disappears at runtime while still
        // looking like a normal entry in the file.
        val problems =
            locales.values.flatMap { locale ->
                val raw = locale.rawKeys.toSet()
                val loaded = locale.loaded.keys
                (raw - loaded).map {
                    "${locale.language}.properties:${locale.lineOf[it]} '$it' is not loaded by Properties (continuation or escape on the previous line?)"
                } +
                    (loaded - raw).map { "${locale.language}: Properties loaded '$it' which no raw line defines" }
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
                locale.loaded.mapNotNull { (key, value) ->
                    val line = locale.lineOf[key]
                    when {
                        value.isBlank() -> "${locale.language}:$line $key has an empty value"
                        !KEY_PATTERN.matches(key) -> "${locale.language}:$line malformed key '$key'"
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
                val keys = locale.rawKeys
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
        // stray '%' in any locale throws the first time that text is displayed. The
        // contract with the caller is the highest argument slot referenced, so a
        // translation may reuse a slot (%1$s twice) or reorder slots freely.
        val enCount = locales.getValue(EN).loaded.mapValues { argumentCount(it.value) }
        val problems =
            locales.values.flatMap { locale ->
                locale.loaded.mapNotNull { (key, value) ->
                    val line = locale.lineOf[key]
                    val count = argumentCount(value)
                    val formatError = runCatching { value.format(*sampleArgs(value)) }.exceptionOrNull()
                    when {
                        formatError != null ->
                            "${locale.language}:$line $key does not format: ${formatError.message}"
                        enCount[key]?.let { it != count } == true ->
                            "${locale.language}:$line $key uses $count argument(s), en uses ${enCount[key]}"
                        else -> null
                    }
                }
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `every key referenced by getText in source exists in English`() {
        val en = locales.getValue(EN).loaded.keys
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
                .loaded
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
