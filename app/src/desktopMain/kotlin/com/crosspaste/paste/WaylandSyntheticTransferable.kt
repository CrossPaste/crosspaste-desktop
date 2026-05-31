package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

/**
 * Bridges a raw MIME → bytes payload (as produced by Wayland's
 * `zwlr_data_control_offer_v1`) into the [Transferable] contract the existing
 * paste pipeline expects — so [DesktopTransferableConsumer] /
 * [com.crosspaste.paste.plugin.type.PasteTypePlugin] can consume Wayland
 * clipboard data without changes.
 *
 * MIME parameters are stripped for the flavor's `humanPresentableName` so
 * plugin identifiers like `text/plain` / `text/html` keep matching, but the
 * `charset=` parameter is honored when decoding bytes → String. This is
 * critical for sources (IntelliJ / JBR, Qt, etc.) that publish multiple
 * variants like `text/plain;charset=utf-8` AND `text/plain;charset=Unicode`
 * (UTF-16 with BOM) for the same content — decoding either as plain UTF-8
 * produces mojibake.
 */
class WaylandSyntheticTransferable(
    payload: Map<String, ByteArray>,
) : Transferable {

    private val entries: List<Entry> = payload.entries.map { Entry(it.key, it.value) }

    private val flavors: Array<DataFlavor> =
        entries
            .map { it.flavor }
            .toTypedArray()

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavors.any { it.equals(flavor) }

    override fun getTransferData(flavor: DataFlavor): Any {
        val entry = entries.firstOrNull { it.flavor.equals(flavor) } ?: error("Unsupported flavor: $flavor")
        return when (entry.kind) {
            EntryKind.TEXT -> String(entry.bytes, entry.charset)
            EntryKind.BINARY -> ByteArrayInputStream(entry.bytes) as InputStream
        }
    }

    // Not a data class: holding a ByteArray + relying on the synthesized
    // equals/hashCode would compare arrays by reference, which is misleading
    // (and tripped detekt's array-in-data-class check). Entry is never used
    // as a map key or in equality checks here, so a plain class is fine.
    private class Entry(
        val rawMime: String,
        val bytes: ByteArray,
    ) {
        val baseMime: String = rawMime.substringBefore(';').trim()
        val kind: EntryKind = if (isTextMime(baseMime, rawMime)) EntryKind.TEXT else EntryKind.BINARY

        /**
         * Charset for decoding [bytes] when [kind] is TEXT. Parsed from the
         * `charset=` parameter; defaults to UTF-8 when unspecified (modern
         * desktop Linux is UTF-8 throughout). `Charset.forName` already
         * resolves Java aliases like `Unicode` → UTF-16.
         */
        val charset: Charset = parseCharsetParam(rawMime) ?: Charsets.UTF_8

        val flavor: DataFlavor =
            when (kind) {
                EntryKind.TEXT ->
                    DataFlavor(
                        "$baseMime; class=java.lang.String; charset=${charset.name()}",
                        baseMime,
                    )
                EntryKind.BINARY ->
                    DataFlavor(
                        "$baseMime; class=java.io.InputStream",
                        baseMime,
                    )
            }
    }

    private enum class EntryKind { TEXT, BINARY }

    companion object {

        // Wayland-native text identifiers occasionally appear without a slash
        // (legacy X selection targets surfaced through XWayland).
        private val LEGACY_TEXT_MIMES = setOf("UTF8_STRING", "STRING", "TEXT")

        private fun isTextMime(
            baseMime: String,
            rawMime: String,
        ): Boolean =
            baseMime.startsWith("text/") ||
                baseMime in LEGACY_TEXT_MIMES ||
                rawMime in LEGACY_TEXT_MIMES

        /**
         * Parse `charset=<name>` from a MIME parameter list. Returns null if
         * absent, malformed, or the charset isn't installed on the JVM.
         */
        private fun parseCharsetParam(rawMime: String): Charset? {
            val params = rawMime.split(';').drop(1)
            for (param in params) {
                val kv = param.split('=', limit = 2)
                if (kv.size == 2 && kv[0].trim().equals("charset", ignoreCase = true)) {
                    val name = kv[1].trim().removeSurrounding("\"")
                    if (name.isEmpty()) return null
                    return try {
                        Charset.forName(name)
                    } catch (_: UnsupportedCharsetException) {
                        null
                    } catch (_: IllegalCharsetNameException) {
                        null
                    }
                }
            }
            return null
        }
    }
}
