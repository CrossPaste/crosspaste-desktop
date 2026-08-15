package com.crosspaste.cli.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins strict UTF-8 decoding of piped stdin: invalid input must fail loudly
 * instead of being silently rewritten with U+FFFD replacement characters.
 */
class StdinDecodeTest {

    @Test
    fun validUtf8DecodesUnchanged() {
        assertEquals("hello 世界 ✓", decodeStdinUtf8("hello 世界 ✓".encodeToByteArray()))
    }

    @Test
    fun invalidSequenceIsRejected() {
        val invalid = byteArrayOf(0x68, 0x69, 0xFF.toByte(), 0xFE.toByte())
        val e = assertFailsWith<StdinReadException> { decodeStdinUtf8(invalid) }
        assertEquals("stdin is not valid UTF-8; only text can be copied", e.message)
    }

    @Test
    fun truncatedMultibyteSequenceIsRejected() {
        // "世" is 0xE4 0xB8 0x96; cut off the final continuation byte
        val truncated = "世".encodeToByteArray().copyOf(2)
        assertFailsWith<StdinReadException> { decodeStdinUtf8(truncated) }
    }
}
