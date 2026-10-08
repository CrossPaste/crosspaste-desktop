package com.crosspaste.paste

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.charset.Charset

/**
 * Turns a Wayland clipboard offer (a list of MIME types plus a way to read
 * each) into the AWT [Transferable] the paste pipeline already understands.
 *
 * AWT is not involved in a native Wayland read, so this does by hand what
 * `XDataTransferer` does for X11 targets: pick, per paste type, the one MIME
 * type worth reading and expose it under the [DataFlavor] whose
 * `humanPresentableName` the matching [com.crosspaste.paste.plugin.type.PasteTypePlugin]
 * identifies — `Unicode String` for text, `text/html`, `image/png`,
 * `application/x-java-file-list`, `text/rtf`.
 *
 * Reads are eager: the offer may be replaced by the next copy at any moment,
 * so everything the pipeline will want is pulled into memory now and the
 * result is a self-contained snapshot (compare [PrefetchedTransferable]).
 * A read that fails leaves its flavor out rather than failing the whole copy.
 */
object WaylandClipboardSnapshot {

    private val logger = KotlinLogging.logger {}

    /** Text MIME types in order of preference, with the charset their bytes carry. */
    private val TEXT_CANDIDATES: List<Pair<String, Charset>> =
        listOf(
            "text/plain;charset=utf-8" to Charsets.UTF_8,
            "UTF8_STRING" to Charsets.UTF_8,
            "text/plain" to Charsets.UTF_8,
            "STRING" to Charsets.ISO_8859_1,
            "TEXT" to Charsets.ISO_8859_1,
        )

    private val HTML_CANDIDATES = listOf("text/html")

    private val RTF_CANDIDATES = listOf("text/rtf", "application/rtf", "text/richtext")

    /** Image MIME types the image plugin identifies, best first. */
    private val IMAGE_CANDIDATES = listOf("image/png", "image/jpeg")

    private const val URI_LIST = "text/uri-list"

    private const val GNOME_COPIED_FILES = "x-special/gnome-copied-files"

    val HTML_FLAVOR = DataFlavor("text/html;class=java.lang.String")

    val RTF_FLAVOR = DataFlavor("text/rtf;class=java.io.InputStream")

    /**
     * Builds the snapshot. [read] returns the bytes of one offered MIME type,
     * or null when it cannot be read (the owner went away, the selection was
     * replaced, the payload is too large). Returns null when nothing usable
     * could be read at all.
     */
    fun create(
        mimeTypes: List<String>,
        read: (String) -> ByteArray?,
    ): Transferable? {
        // `receive` must quote the type exactly as offered, so matching is done
        // on a normalized form but the original spelling is what gets read.
        val offered = mimeTypes.associateBy(::normalize)

        fun firstOffered(candidates: List<String>): String? =
            candidates.firstNotNullOfOrNull { candidate -> offered[normalize(candidate)] }

        val values = LinkedHashMap<DataFlavor, Any>()

        // Files first: when files are on the clipboard every other representation
        // (icon image, "file:///" text) describes them and the plugins skip it.
        val uriListMime = firstOffered(listOf(URI_LIST))
        val gnomeFilesMime = firstOffered(listOf(GNOME_COPIED_FILES))
        val files =
            uriListMime
                ?.let { mime ->
                    read(mime)?.let { parseFileUris(it.decodeToString()) }
                }?.takeIf { it.isNotEmpty() }
                ?: gnomeFilesMime
                    ?.let { mime -> read(mime)?.let { parseFileUris(it.decodeToString()) } }
                    ?.takeIf { it.isNotEmpty() }
        if (files != null) {
            values[DataFlavor.javaFileListFlavor] = files
        }

        if (files == null) {
            IMAGE_CANDIDATES
                .firstNotNullOfOrNull { candidate -> offered[normalize(candidate)]?.let { candidate to it } }
                ?.let { (candidate, mime) ->
                    read(mime)?.let { bytes ->
                        values[DataFlavor("$candidate;class=java.io.InputStream")] = bytes
                    }
                }
        }

        firstOffered(HTML_CANDIDATES)?.let { mime ->
            read(mime)?.let { bytes ->
                values[HTML_FLAVOR] = HtmlClipboardDecoder.decode(bytes, knownCharset = null)
            }
        }

        firstOffered(RTF_CANDIDATES)?.let { mime ->
            read(mime)?.let { bytes -> values[RTF_FLAVOR] = bytes }
        }

        TEXT_CANDIDATES
            .firstNotNullOfOrNull { (candidate, charset) -> offered[normalize(candidate)]?.let { it to charset } }
            ?.let { (mime, charset) ->
                read(mime)?.let { bytes -> values[DataFlavor.stringFlavor] = String(bytes, charset) }
            }

        if (values.isEmpty()) {
            logger.debug { "Nothing readable among offered types: $mimeTypes" }
            return null
        }
        return SnapshotTransferable(values)
    }

    /**
     * Parses a `text/uri-list` (or the `x-special/gnome-copied-files` body,
     * whose first line is the `copy`/`cut` verb) into local files. Comment
     * lines and non-`file:` URIs are skipped.
     */
    fun parseFileUris(body: String): List<File> =
        body
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                runCatching { URI(line) }
                    .getOrNull()
                    ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
                    ?.let { uri -> runCatching { File(uri) }.getOrNull() }
            }.toList()

    /** MIME types compare case-insensitively and ignore whitespace around parameters. */
    private fun normalize(mimeType: String): String = mimeType.replace(" ", "").lowercase()

    private class SnapshotTransferable(
        private val values: LinkedHashMap<DataFlavor, Any>,
    ) : Transferable {

        private val flavors = values.keys.toTypedArray()

        override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.clone()

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = values.containsKey(flavor)

        override fun getTransferData(flavor: DataFlavor?): Any {
            val value = values[flavor] ?: throw UnsupportedFlavorException(flavor)
            // Byte payloads are handed out as a fresh stream per read so the
            // snapshot can be consumed more than once.
            return if (value is ByteArray) ByteArrayInputStream(value) else value
        }
    }
}
