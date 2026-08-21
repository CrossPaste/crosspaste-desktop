package com.crosspaste.cli.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SixelImageTest {

    private val esc = 27.toChar().toString()

    private val red = 0xFF0000
    private val green = 0x00FF00
    private val blue = 0x0000FF

    /** Builds RGBA8888 from a per-pixel packed 0xRRGGBB color, or null for transparent. */
    private fun rgba(
        width: Int,
        height: Int,
        pixel: (x: Int, y: Int) -> Int?,
    ): ByteArray {
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val o = (y * width + x) * 4
                val color = pixel(x, y)
                if (color == null) {
                    bytes[o + 3] = 0
                } else {
                    bytes[o] = (color shr 16).toByte()
                    bytes[o + 1] = (color shr 8).toByte()
                    bytes[o + 2] = color.toByte()
                    bytes[o + 3] = 0xFF.toByte()
                }
            }
        }
        return bytes
    }

    private fun sixelSequence(
        bytes: ByteArray,
        width: Int,
        height: Int,
    ): String = buildString { writeSixelInlineImage(bytes, width, height, write = { append(it) }) }

    @Test
    fun goldenSingleColorColumnTrimsTrailingTransparency() {
        // 2x6: left column red, right column transparent. All six band rows
        // set -> mask 63 -> char '~'; the empty right column is trimmed.
        val bytes = rgba(2, 6) { x, _ -> if (x == 0) red else null }
        assertEquals(
            "${esc}P0;1;0q\"1;1;2;6#0;2;100;0;0#0~$esc\\",
            sixelSequence(bytes, 2, 6),
        )
    }

    @Test
    fun goldenRunLengthEncodesRunsOfFourOrMore() {
        // 8x3 solid blue: rows 0..2 -> mask 7 -> char 'F', run of 8 -> !8F
        val bytes = rgba(8, 3) { _, _ -> blue }
        assertEquals(
            "${esc}P0;1;0q\"1;1;8;3#0;2;0;0;100#0!8F$esc\\",
            sixelSequence(bytes, 8, 3),
        )
    }

    @Test
    fun goldenShortRunsStaySpelledOut() {
        // Runs of 3 are cheaper literal than as !3<ch>
        val bytes = rgba(3, 6) { _, _ -> red }
        assertEquals(
            "${esc}P0;1;0q\"1;1;3;6#0;2;100;0;0#0~~~$esc\\",
            sixelSequence(bytes, 3, 6),
        )
    }

    @Test
    fun goldenTwoColorPlanesSeparatedByCarriageReturn() {
        // 2x6 red|blue: plane #0 paints column 0 ('~', column 1 trimmed),
        // '$' returns to the band start, plane #1 skips column 0 ('?')
        val bytes = rgba(2, 6) { x, _ -> if (x == 0) red else blue }
        assertEquals(
            "${esc}P0;1;0q\"1;1;2;6#0;2;100;0;0#1;2;0;0;100#0~$#1?~$esc\\",
            sixelSequence(bytes, 2, 6),
        )
    }

    @Test
    fun goldenMultipleBandsSeparatedByNewline() {
        // 1x8 red: full first band ('~'), then a partial band of two rows
        // (mask 3 -> 'B') after the '-' separator
        val bytes = rgba(1, 8) { _, _ -> red }
        assertEquals(
            "${esc}P0;1;0q\"1;1;1;8#0;2;100;0;0#0~-#0B$esc\\",
            sixelSequence(bytes, 1, 8),
        )
    }

    @Test
    fun fullyTransparentBandStillEmitsItsSeparator() {
        // 1x12 with rows 0..5 transparent, rows 6..11 red: the empty first
        // band must still advance the cursor with '-'
        val bytes = rgba(1, 12) { _, y -> if (y < 6) null else red }
        assertEquals(
            "${esc}P0;1;0q\"1;1;1;12#0;2;100;0;0-#0~$esc\\",
            sixelSequence(bytes, 1, 12),
        )
    }

    @Test
    fun fullyTransparentImageEmitsHeaderOnly() {
        val bytes = rgba(2, 2) { _, _ -> null }
        assertEquals(
            "${esc}P0;1;0q\"1;1;2;2$esc\\",
            sixelSequence(bytes, 2, 2),
        )
    }

    @Test
    fun alphaBelowThresholdIsTransparentAtThresholdIsOpaque() {
        val bytes = rgba(2, 1) { _, _ -> red }
        bytes[3] = (SIXEL_OPAQUE_ALPHA_THRESHOLD - 1).toByte()
        bytes[7] = SIXEL_OPAQUE_ALPHA_THRESHOLD.toByte()
        val quantized = quantizeForSixel(bytes, 2, 1)
        assertEquals(-1, quantized.indices[0])
        assertEquals(0, quantized.indices[1])
    }

    @Test
    fun paletteKeepsFirstSeenOrderWhenColorsFit() {
        val bytes =
            rgba(3, 1) { x, _ ->
                when (x) {
                    0 -> green
                    1 -> red
                    else -> blue
                }
            }
        val quantized = quantizeForSixel(bytes, 3, 1)
        assertEquals(listOf(green, red, blue), quantized.palette.toList())
        assertEquals(listOf(0, 1, 2), quantized.indices.toList())
    }

    @Test
    fun manyDistinctColorsQuantizeToAtMost256() {
        // 32x32 with 1024 distinct colors forces the median-cut path
        val bytes =
            rgba(32, 32) { x, y ->
                val i = y * 32 + x
                (i shl 12) or i
            }
        val quantized = quantizeForSixel(bytes, 32, 32)
        assertEquals(SIXEL_MAX_COLORS, quantized.palette.size)
        assertTrue(quantized.indices.all { it in 0 until SIXEL_MAX_COLORS })
        assertTrue(quantized.palette.all { it in 0..0xFFFFFF })
    }

    @Test
    fun medianCutSplitsAlongWidestAxisAndAveragesByWeight() {
        // Two near-black colors vs a heavy red cluster; with two boxes the
        // split must isolate red (widest axis), and the dark box's average
        // rounds to 0x000001 with equal weights
        val result =
            medianCutPalette(
                distinctColors = intArrayOf(0x000000, 0x000001, 0xFF0000),
                counts = intArrayOf(1, 1, 100),
                maxColors = 2,
            )
        assertEquals(listOf(0x000001, 0xFF0000), result.palette.toList())
        assertEquals(0, result.colorToIndex.getValue(0x000000))
        assertEquals(0, result.colorToIndex.getValue(0x000001))
        assertEquals(1, result.colorToIndex.getValue(0xFF0000))
    }

    @Test
    fun medianCutWithEnoughBoxesKeepsColorsExact() {
        val colors = intArrayOf(red, green, blue)
        val result = medianCutPalette(colors, intArrayOf(5, 3, 1), maxColors = 8)
        assertEquals(colors.toSet(), result.palette.toSet())
        for (color in colors) {
            assertEquals(color, result.palette[result.colorToIndex.getValue(color)])
        }
    }

    @Test
    fun zeroSizeImageWritesNothing() {
        assertEquals("", sixelSequence(ByteArray(0), 0, 0))
    }

    @Test
    fun undersizedBufferIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            quantizeForSixel(ByteArray(7), 2, 1)
        }
    }

    @Test
    fun outputContainsNoLiteralControlBytesBesidesEscapes() {
        // Every byte between DCS and ST must be printable ASCII — a mask can
        // never exceed 63, so chars stay in 63..126
        val bytes =
            rgba(16, 16) { x, y ->
                if ((x + y) % 3 == 0) null else ((x * 16 + y) shl 8) or 0x40
            }
        val sequence = sixelSequence(bytes, 16, 16)
        val inner = sequence.removePrefix("${esc}P").removeSuffix("$esc\\")
        assertTrue(inner.all { it.code in 32..126 }, "unexpected control byte in sixel payload")
    }
}
