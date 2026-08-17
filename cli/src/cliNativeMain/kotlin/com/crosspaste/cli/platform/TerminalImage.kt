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

private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

internal fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= PNG_MAGIC.size && PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] }

/** OSC 1337: ESC ] 1337 ; File = inline=1;... : <base64> BEL */
@OptIn(ExperimentalEncodingApi::class)
internal fun buildItermInlineImage(
    name: String,
    bytes: ByteArray,
): String {
    val nameB64 = Base64.encode(name.encodeToByteArray())
    return "$TERM_ESC]1337;File=inline=1;size=${bytes.size};name=$nameB64:${Base64.encode(bytes)}$TERM_BEL"
}

/**
 * Kitty graphics protocol: APC chunks of at most 4096 base64 chars; control
 * keys (a=T transmit-and-display, f=100 PNG) go on the first chunk only,
 * m=1 marks continuation and m=0 the final chunk.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun buildKittyInlineImage(bytes: ByteArray): String {
    val b64 = Base64.encode(bytes)
    val sb = StringBuilder()
    var offset = 0
    var first = true
    while (offset < b64.length) {
        val end = minOf(offset + 4096, b64.length)
        val more = if (end < b64.length) 1 else 0
        sb.append(TERM_ESC).append("_G")
        if (first) {
            sb.append("a=T,f=100,")
            first = false
        }
        sb.append("m=$more;")
        sb.append(b64, offset, end)
        sb.append(TERM_ESC).append("\\")
        offset = end
    }
    return sb.toString()
}
