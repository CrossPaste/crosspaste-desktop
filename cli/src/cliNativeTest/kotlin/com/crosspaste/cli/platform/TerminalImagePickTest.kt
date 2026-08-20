package com.crosspaste.cli.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalImagePickTest {

    private fun pngHeader(
        width: Int,
        height: Int,
    ): ByteArray {
        val bytes = ByteArray(24)
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(bytes)
        // IHDR chunk length (13) then the type tag
        bytes[11] = 13
        "IHDR".forEachIndexed { i, c -> bytes[12 + i] = c.code.toByte() }
        for (i in 0 until 4) {
            bytes[16 + i] = (width shr (24 - i * 8)).toByte()
            bytes[20 + i] = (height shr (24 - i * 8)).toByte()
        }
        return bytes
    }

    @Test
    fun pngDimensionsComeFromTheIhdrHeader() {
        assertEquals(1920 to 1080, parsePngDimensions(pngHeader(1920, 1080)))
        assertNull(parsePngDimensions(ByteArray(24)))
        assertNull(parsePngDimensions(pngHeader(1920, 1080).copyOf(20)))
        assertNull(parsePngDimensions(pngHeader(0, 5)))
    }

    @Test
    fun cellBoxPreservesAspectInsideTheLimits() {
        // A 2:1-wide image in a 40x10 box: width-bound, cells are 1:2
        assertEquals(40 to 10, fitImageCellBox(2000, 1000, 40, 10))
        // A tall image is height-bound and narrow
        val (cols, rows) = fitImageCellBox(500, 2000, 40, 10)
        assertEquals(10, rows)
        assertEquals(5, cols)
        // An extreme sliver still occupies at least one column
        assertEquals(1 to 10, fitImageCellBox(1, 10000, 40, 10))
    }

    @Test
    fun itermSequenceCarriesTheDisplayBoxInCells() {
        val out =
            buildString {
                writeItermInlineImage("a.png", byteArrayOf(1, 2, 3), { append(it) }, widthCells = 12, heightCells = 5)
            }
        assertTrue("width=12;" in out)
        assertTrue("height=5;" in out)
        // Unbounded stays parameter-free
        val plain = buildString { writeItermInlineImage("a.png", byteArrayOf(1), { append(it) }) }
        assertTrue("width=" !in plain && "height=" !in plain)
    }

    @Test
    fun kittySequenceCarriesTheDisplayBoxAndDeleteIsWellFormed() {
        val out =
            buildString {
                writeKittyInlineImage(ByteArray(10), { append(it) }, columns = 12, rows = 5)
            }
        assertTrue("c=12," in out)
        assertTrue("r=5," in out)
        val delete = buildString { kittyDeleteImages { append(it) } }
        assertEquals("${27.toChar()}_Ga=d${27.toChar()}\\", delete)
    }
}
