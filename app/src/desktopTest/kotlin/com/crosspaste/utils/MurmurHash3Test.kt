package com.crosspaste.utils

import org.junit.jupiter.api.Assertions.assertArrayEquals
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class MurmurHash3Test {

    @Test
    fun testHash() {
        val hash = getCodecsUtils().hash("test".encodeToByteArray())
        val streamingMurmurHash3 = StreamingMurmurHash3(seed = 13043025u)
        streamingMurmurHash3.update("test".encodeToByteArray())
        val (hash1, hash2) = streamingMurmurHash3.finish()
        val streamHash =
            buildString(32) {
                appendHex(hash1)
                appendHex(hash2)
            }
        assertEquals(hash, streamHash, "Hashes should match")
    }

    @Test
    fun `test empty input`() {
        val seed = 42u
        val input = byteArrayOf()

        val regular = MurmurHash3(seed).hash128x64(input)

        val streaming = StreamingMurmurHash3(seed)
        streaming.update(input)
        val streamingResult = streaming.finish()

        assertArrayEquals(regular, streamingResult, "Empty input should produce same result")
    }

    @Test
    fun `test single byte input`() {
        val seed = 0u
        val input = byteArrayOf(0x42)

        val regular = MurmurHash3(seed).hash128x64(input)

        val streaming = StreamingMurmurHash3(seed)
        streaming.update(input)
        val streamingResult = streaming.finish()

        assertArrayEquals(regular, streamingResult, "Single byte input should produce same result")
    }

    @Test
    fun `test streaming updates vs single update`() {
        val seed = 999u
        val input = "Hello, World! This is a test string for MurmurHash3.".toByteArray()

        // Single update
        val streaming1 = StreamingMurmurHash3(seed)
        streaming1.update(input)
        val result1 = streaming1.finish()

        // Chunked update
        val streaming2 = StreamingMurmurHash3(seed)
        val chunkSize = 5
        for (i in input.indices step chunkSize) {
            val end = minOf(i + chunkSize, input.size)
            streaming2.update(input, i, end - i)
        }
        val result2 = streaming2.finish()

        // Compare with original algorithm
        val regular = MurmurHash3(seed).hash128x64(input)

        assertArrayEquals(regular, result1, "Single streaming update should match regular")
        assertArrayEquals(regular, result2, "Chunked streaming update should match regular")
        assertArrayEquals(result1, result2, "Single and chunked streaming should match")
    }

    @Test
    fun `test different seeds`() {
        val input = "Test string".toByteArray()
        val seeds = listOf(0u, 1u, 42u, 0xDEADBEEFu, UInt.MAX_VALUE)

        for (seed in seeds) {
            val regular = MurmurHash3(seed).hash128x64(input)

            val streaming = StreamingMurmurHash3(seed)
            streaming.update(input)
            val streamingResult = streaming.finish()

            assertArrayEquals(regular, streamingResult, "Seed $seed should produce same result")
        }
    }

    @Test
    fun `test boundary cases`() {
        val seed = 42u

        // Test exactly 16-byte input
        val input16 = ByteArray(16) { it.toByte() }
        val regular16 = MurmurHash3(seed).hash128x64(input16)
        val streaming16 = StreamingMurmurHash3(seed)
        streaming16.update(input16)
        val streamingResult16 = streaming16.finish()
        assertArrayEquals(regular16, streamingResult16, "16-byte input should match")

        // Test 15-byte input
        val input15 = ByteArray(15) { it.toByte() }
        val regular15 = MurmurHash3(seed).hash128x64(input15)
        val streaming15 = StreamingMurmurHash3(seed)
        streaming15.update(input15)
        val streamingResult15 = streaming15.finish()
        assertArrayEquals(regular15, streamingResult15, "15-byte input should match")

        // Test 17-byte input
        val input17 = ByteArray(17) { it.toByte() }
        val regular17 = MurmurHash3(seed).hash128x64(input17)
        val streaming17 = StreamingMurmurHash3(seed)
        streaming17.update(input17)
        val streamingResult17 = streaming17.finish()
        assertArrayEquals(regular17, streamingResult17, "17-byte input should match")
    }

    @Test
    fun `test known test vectors`() {
        // Some known test vectors
        val testCases =
            listOf(
                TestCase("", 0u),
                TestCase("a", 0u),
                TestCase("abc", 0u),
                TestCase("message digest", 0u),
                TestCase("abcdefghijklmnopqrstuvwxyz", 0u),
                TestCase("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", 0u),
            )

        for (testCase in testCases) {
            val input = testCase.input.toByteArray()
            val seed = testCase.seed

            val regular = MurmurHash3(seed).hash128x64(input)

            val streaming = StreamingMurmurHash3(seed)
            streaming.update(input)
            val streamingResult = streaming.finish()

            assertArrayEquals(
                regular,
                streamingResult,
                "Test case '${testCase.input}' with seed ${testCase.seed} should match",
            )
        }
    }

    @Test
    fun `stress test with large input`() {
        val seed = 12345u
        val largeInput = Random.nextBytes(10000)

        val regular = MurmurHash3(seed).hash128x64(largeInput)

        // Test with various chunk sizes
        val chunkSizes = listOf(1, 3, 7, 16, 17, 100, 1000)

        for (chunkSize in chunkSizes) {
            val streaming = StreamingMurmurHash3(seed)
            var offset = 0
            while (offset < largeInput.size) {
                val actualChunkSize = minOf(chunkSize, largeInput.size - offset)
                streaming.update(largeInput, offset, actualChunkSize)
                offset += actualChunkSize
            }
            val streamingResult = streaming.finish()

            assertArrayEquals(
                regular,
                streamingResult,
                "Large input with chunk size $chunkSize should match",
            )
        }
    }

    private data class TestCase(
        val input: String,
        val seed: UInt,
    )
}
