package com.crosspaste.paste

import com.ibm.icu.text.CharsetDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

/**
 * Decodes raw clipboard `text/html` bytes into a String by detecting the actual
 * encoding instead of trusting whatever charset the toolkit declared.
 *
 * Resolution order (most authoritative first):
 *  1. A leading byte-order mark (UTF-8 / UTF-16 / UTF-32).
 *  2. An in-document declaration: `<meta charset>`, the `charset=` of a
 *     `<meta http-equiv="Content-Type">`, or an XML `encoding="…"` prolog.
 *  3. ICU4J's statistical [CharsetDetector].
 *  4. UTF-8 as a last resort (modern desktop Linux is UTF-8 throughout).
 *
 * This is the counterpart to reading the X11 selection bytes directly: it makes
 * the decode independent of AWT's hard-coded `text/html` → UTF-16 assumption.
 */
object HtmlClipboardDecoder {

    private val logger = KotlinLogging.logger {}

    // charset="x" / charset='x' / charset=x  (from <meta> or an http-equiv content type)
    private val META_CHARSET = Regex("""charset\s*=\s*["']?\s*([a-zA-Z0-9_\-:.]+)""", RegexOption.IGNORE_CASE)

    // encoding="x" from an <?xml … ?> prolog
    private val XML_ENCODING =
        Regex("""<\?xml[^>]*\bencoding\s*=\s*["']([a-zA-Z0-9_\-:.]+)["']""", RegexOption.IGNORE_CASE)

    // Only the head matters for a charset declaration; scan a bounded prefix.
    private const val DECLARATION_SCAN_LIMIT = 4096

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        detectByBom(bytes)?.let { (charset, bomLength) ->
            return String(bytes, bomLength, bytes.size - bomLength, charset)
        }

        resolveDeclaredCharset(bytes)?.let { charset ->
            logger.info { "Decoding clipboard html as declared charset ${charset.name()}" }
            return String(bytes, charset)
        }

        detectByContent(bytes)?.let { charset ->
            logger.info { "Decoding clipboard html as detected charset ${charset.name()}" }
            return String(bytes, charset)
        }

        return String(bytes, Charsets.UTF_8)
    }

    private fun detectByBom(bytes: ByteArray): Pair<Charset, Int>? {
        fun has(vararg prefix: Int): Boolean =
            bytes.size >= prefix.size && prefix.withIndex().all { (i, b) -> bytes[i].toInt() and 0xff == b }

        return when {
            has(0x00, 0x00, 0xFE, 0xFF) -> Charset.forName("UTF-32BE") to 4
            has(0xFF, 0xFE, 0x00, 0x00) -> Charset.forName("UTF-32LE") to 4
            has(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            has(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            has(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            else -> null
        }
    }

    private fun resolveDeclaredCharset(bytes: ByteArray): Charset? {
        // Decode the head leniently as ISO-8859-1 so the ASCII declaration is
        // readable regardless of the real (still unknown) encoding.
        val headLength = minOf(bytes.size, DECLARATION_SCAN_LIMIT)
        val head = String(bytes, 0, headLength, Charsets.ISO_8859_1)

        val name =
            XML_ENCODING.find(head)?.groupValues?.get(1)
                ?: META_CHARSET.find(head)?.groupValues?.get(1)
        return name?.let { toCharsetOrNull(it) }
    }

    private fun detectByContent(bytes: ByteArray): Charset? =
        runCatching {
            val detector = CharsetDetector()
            detector.setText(bytes)
            detector.detect()?.name?.let { toCharsetOrNull(it) }
        }.getOrNull()

    private fun toCharsetOrNull(name: String): Charset? =
        try {
            Charset.forName(name.trim())
        } catch (_: UnsupportedCharsetException) {
            null
        } catch (_: IllegalCharsetNameException) {
            null
        }
}
