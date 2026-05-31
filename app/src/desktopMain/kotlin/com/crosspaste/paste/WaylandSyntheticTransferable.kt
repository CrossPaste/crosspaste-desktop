package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Bridges a raw MIME → bytes payload (as produced by Wayland's
 * `zwlr_data_control_offer_v1`) into the [Transferable] contract the existing
 * paste pipeline expects — so [DesktopTransferableConsumer] /
 * [com.crosspaste.paste.plugin.type.PasteTypePlugin] can consume Wayland
 * clipboard data without changes.
 *
 * MIME parameters (e.g. `;charset=utf-8`) are stripped for the flavor's
 * `humanPresentableName` so plugin identifiers like `text/plain` / `text/html`
 * keep matching.
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
            EntryKind.TEXT -> String(entry.bytes, Charsets.UTF_8)
            EntryKind.BINARY -> ByteArrayInputStream(entry.bytes) as InputStream
        }
    }

    private data class Entry(
        val rawMime: String,
        val bytes: ByteArray,
    ) {
        val baseMime: String = rawMime.substringBefore(';').trim()
        val kind: EntryKind = if (isTextMime(baseMime, rawMime)) EntryKind.TEXT else EntryKind.BINARY
        val flavor: DataFlavor =
            when (kind) {
                EntryKind.TEXT ->
                    DataFlavor(
                        "$baseMime; class=java.lang.String; charset=UTF-8",
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
    }
}
