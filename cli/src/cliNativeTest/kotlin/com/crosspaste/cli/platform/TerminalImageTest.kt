package com.crosspaste.cli.platform

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class TerminalImageTest {

    private val esc = 27.toChar().toString()
    private val bel = 7.toChar().toString()

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    private fun itermSequence(
        name: String,
        bytes: ByteArray,
    ): String = buildString { writeItermInlineImage(name, bytes, write = { append(it) }) }

    private fun kittySequence(bytes: ByteArray): String =
        buildString { writeKittyInlineImage(bytes, write = { append(it) }) }

    @Test
    fun detectsKnownTerminals() {
        assertEquals(
            TerminalImageProtocol.ITERM,
            detectTerminalImageProtocol(env("TERM_PROGRAM" to "iTerm.app", "TERM" to "xterm-256color")),
        )
        assertEquals(
            TerminalImageProtocol.ITERM,
            detectTerminalImageProtocol(env("TERM_PROGRAM" to "WezTerm", "TERM" to "xterm-256color")),
        )
        assertEquals(
            TerminalImageProtocol.KITTY,
            detectTerminalImageProtocol(env("TERM" to "xterm-kitty")),
        )
        assertEquals(
            TerminalImageProtocol.KITTY,
            detectTerminalImageProtocol(env("KITTY_WINDOW_ID" to "1", "TERM" to "xterm-256color")),
        )
        assertEquals(
            TerminalImageProtocol.KITTY,
            detectTerminalImageProtocol(env("TERM" to "xterm-ghostty")),
        )
    }

    @Test
    fun unknownTerminalGetsNoProtocol() {
        assertNull(detectTerminalImageProtocol(env("TERM" to "xterm-256color")))
        assertNull(detectTerminalImageProtocol(env()))
    }

    @Test
    fun multiplexersFallBackEvenInsideASupportedTerminal() {
        assertNull(
            detectTerminalImageProtocol(
                env("TMUX" to "/tmp/tmux-501/default,123,0", "TERM_PROGRAM" to "iTerm.app"),
            ),
        )
        assertNull(
            detectTerminalImageProtocol(env("TERM" to "screen-256color", "TERM_PROGRAM" to "iTerm.app")),
        )
        assertNull(
            detectTerminalImageProtocol(env("TERM" to "tmux-256color", "KITTY_WINDOW_ID" to "1")),
        )
    }

    @Test
    fun pngMagicIsRecognized() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        assertTrue(isPng(png))
        assertFalse(isPng(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) // JPEG
        assertFalse(isPng(ByteArray(0)))
    }

    @Test
    fun itermSequenceWrapsBase64PayloadAndName() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val sequence = itermSequence("shot.png", bytes)
        val expectedName = Base64.encode("shot.png".encodeToByteArray())
        assertEquals(
            "$esc]1337;File=inline=1;size=4;name=$expectedName:${Base64.encode(bytes)}$bel",
            sequence,
        )
    }

    @Test
    fun itermChunkedEncodingEqualsWholeInputEncoding() {
        // Chunking at a multiple of 3 source bytes must concatenate to the
        // exact whole-input base64 (no mid-stream padding)
        val bytes = ByteArray(9001) { (it % 251).toByte() }
        val sequence = itermSequence("a.png", bytes)
        assertEquals(
            Base64.encode(bytes),
            sequence.substringAfter(':').removeSuffix(bel),
        )
    }

    @Test
    fun kittySingleChunkCarriesControlKeysAndFinalMarker() {
        val bytes = byteArrayOf(9, 8, 7)
        val sequence = kittySequence(bytes)
        assertEquals("${esc}_Ga=T,f=100,m=0;${Base64.encode(bytes)}$esc\\", sequence)
    }

    @Test
    fun kittyLargePayloadIsChunkedAndReassembles() {
        // 9000 bytes -> 12000 base64 chars -> 3 chunks (4096 + 4096 + 3808)
        val bytes = ByteArray(9000) { (it % 251).toByte() }
        val sequence = kittySequence(bytes)
        val chunks =
            sequence
                .split("$esc\\")
                .filter { it.isNotEmpty() }
                .map { it.removePrefix("${esc}_G") }
        assertEquals(3, chunks.size)
        assertTrue(chunks[0].startsWith("a=T,f=100,m=1;"))
        assertTrue(chunks[1].startsWith("m=1;"))
        assertTrue(chunks[2].startsWith("m=0;"))
        val payload = chunks.joinToString("") { it.substringAfter(';') }
        assertEquals(Base64.encode(bytes), payload)
        // No chunk may exceed the protocol's 4096-char payload limit
        assertTrue(chunks.all { it.substringAfter(';').length <= 4096 })
    }
}
