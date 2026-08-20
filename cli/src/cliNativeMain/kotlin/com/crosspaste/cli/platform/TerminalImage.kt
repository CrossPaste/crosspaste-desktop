package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Inline terminal image rendering (design: the CLI never decodes pixels —
 * both protocols accept the encoded image file as-is, base64-wrapped in an
 * escape sequence, and the terminal does the decoding).
 */
enum class TerminalImageProtocol {
    /** iTerm2 OSC 1337 `File=` protocol; accepts any common image format. */
    ITERM,

    /** Kitty graphics protocol; PNG payloads only (`f=100`). */
    KITTY,
}

@OptIn(ExperimentalForeignApi::class)
fun detectTerminalImageProtocol(): TerminalImageProtocol? = detectTerminalImageProtocol { getenv(it)?.toKString() }

/**
 * Environment-based detection, injectable for tests. Deliberately
 * conservative: an unrecognized terminal gets the path fallback rather than
 * escape garbage.
 */
internal fun detectTerminalImageProtocol(env: (String) -> String?): TerminalImageProtocol? {
    // Inside tmux/screen the sequences would need passthrough wrapping that
    // the multiplexer may not allow; fall back to printing paths.
    if (!env("TMUX").isNullOrEmpty()) return null
    val term = env("TERM").orEmpty()
    if (term.startsWith("tmux") || term.startsWith("screen")) return null
    val termProgram = env("TERM_PROGRAM").orEmpty()
    return when {
        term.contains("kitty") || !env("KITTY_WINDOW_ID").isNullOrEmpty() -> TerminalImageProtocol.KITTY
        term.contains("ghostty") || termProgram.equals("ghostty", ignoreCase = true) -> TerminalImageProtocol.KITTY
        termProgram == "iTerm.app" || termProgram == "WezTerm" || termProgram == "mintty" -> TerminalImageProtocol.ITERM
        else -> null
    }
}

// Spelled out as char codes so the source contains no literal control bytes
internal val TERM_ESC: String = 27.toChar().toString()
internal val TERM_BEL: String = 7.toChar().toString()

internal const val PNG_SIGNATURE_SIZE = 8

private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

internal fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= PNG_MAGIC.size && PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] }

/**
 * Base64-encode in slices of 3072 source bytes: a multiple of 3, so chunk
 * outputs concatenate to exactly the whole-input encoding (no mid-stream
 * padding), and each chunk is exactly 4096 chars — the Kitty protocol's
 * per-chunk payload limit. Streaming through [write] keeps only one small
 * chunk in memory instead of the full base64 plus protocol string.
 */
private const val BASE64_CHUNK_SOURCE_BYTES = 3072

/**
 * OSC 1337: ESC ] 1337 ; File = inline=1;... : <base64> BEL
 *
 * [widthCells]/[heightCells] bound the DISPLAY size in terminal cells (bare
 * numbers mean cells in this protocol); the terminal letterboxes inside that
 * box since preserveAspectRatio defaults to 1. The transmitted bytes are the
 * stored file either way — the CLI never rescales pixels.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun writeItermInlineImage(
    name: String,
    bytes: ByteArray,
    write: (String) -> Unit,
    widthCells: Int? = null,
    heightCells: Int? = null,
) {
    val nameB64 = Base64.encode(name.encodeToByteArray())
    val sizeParams =
        buildString {
            widthCells?.let { append("width=$it;") }
            heightCells?.let { append("height=$it;") }
        }
    write("$TERM_ESC]1337;File=inline=1;size=${bytes.size};$sizeParams" + "name=$nameB64:")
    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + BASE64_CHUNK_SOURCE_BYTES, bytes.size)
        write(Base64.encode(bytes, offset, end))
        offset = end
    }
    write(TERM_BEL)
}

/**
 * Kitty graphics protocol: APC chunks of at most 4096 base64 chars; control
 * keys (a=T transmit-and-display, f=100 PNG) go on the first chunk only,
 * m=1 marks continuation and m=0 the final chunk.
 *
 * [columns]/[rows] bound the display area in cells. Kitty scales the image
 * to FILL that rectangle without preserving aspect, so callers must pre-fit
 * the box with [fitImageCellBox] using the PNG's header dimensions.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun writeKittyInlineImage(
    bytes: ByteArray,
    write: (String) -> Unit,
    columns: Int? = null,
    rows: Int? = null,
) {
    var offset = 0
    var first = true
    while (offset < bytes.size) {
        val end = minOf(offset + BASE64_CHUNK_SOURCE_BYTES, bytes.size)
        val more = if (end < bytes.size) 1 else 0
        val sb = StringBuilder()
        sb.append(TERM_ESC).append("_G")
        if (first) {
            sb.append("a=T,f=100,")
            columns?.let { sb.append("c=$it,") }
            rows?.let { sb.append("r=$it,") }
            first = false
        }
        sb.append("m=").append(more).append(';')
        sb.append(Base64.encode(bytes, offset, end))
        sb.append(TERM_ESC).append('\\')
        write(sb.toString())
        offset = end
    }
}

/** Deletes all visible kitty image placements (a=d, default d=a). */
internal fun kittyDeleteImages(write: (String) -> Unit) {
    write("$TERM_ESC" + "_Ga=d$TERM_ESC\\")
}

/**
 * Reads the pixel dimensions from a PNG's IHDR header — plain metadata in
 * the first 24 bytes, NOT pixel decoding (which the CLI never does). Returns
 * null unless the signature and IHDR chunk are where the spec puts them.
 */
internal fun parsePngDimensions(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 24 || !isPng(bytes)) return null
    // Bytes 12..15 must spell "IHDR"; width and height follow big-endian
    if (bytes[12] != 'I'.code.toByte() ||
        bytes[13] != 'H'.code.toByte() ||
        bytes[14] != 'D'.code.toByte() ||
        bytes[15] != 'R'.code.toByte()
    ) {
        return null
    }
    val width = readBigEndianInt(bytes, 16)
    val height = readBigEndianInt(bytes, 20)
    if (width <= 0 || height <= 0) return null
    return width to height
}

private fun readBigEndianInt(
    bytes: ByteArray,
    offset: Int,
): Int =
    ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

/**
 * Fits an image into a cell box preserving aspect ratio, assuming the usual
 * ~1:2 terminal cell (a cell is about twice as tall as wide in pixels).
 */
internal fun fitImageCellBox(
    pixelWidth: Int,
    pixelHeight: Int,
    maxColumns: Int,
    maxRows: Int,
): Pair<Int, Int> {
    val scale =
        minOf(
            maxColumns.toFloat() / pixelWidth,
            maxRows * 2f / pixelHeight,
        )
    val columns = (pixelWidth * scale).toInt().coerceIn(1, maxColumns)
    val rows = (pixelHeight * scale / 2f).toInt().coerceIn(1, maxRows)
    return columns to rows
}
