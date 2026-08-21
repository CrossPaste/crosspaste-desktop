package com.crosspaste.cli

import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliImageTranscoderTest {

    /** Encodes a solid-[argb]-filled PNG of the given size via Skia. */
    private fun pngBytes(
        width: Int,
        height: Int,
        argb: Int,
    ): ByteArray =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(argb)
            surface.makeImageSnapshot().encodeToData()!!.bytes
        }

    @Test
    fun decodesAtOriginalSizeWhenItFitsTheBox() {
        val raw = CliImageTranscoder.transcode(pngBytes(4, 2, 0xFFFF0000.toInt()), 100, 100)
        assertNotNull(raw)
        assertEquals(4, raw.width)
        assertEquals(2, raw.height)
        assertEquals(4 * 2 * 4, raw.rgba.size)
        // Straight-alpha RGBA byte order: opaque red
        assertEquals(0xFF.toByte(), raw.rgba[0])
        assertEquals(0x00.toByte(), raw.rgba[1])
        assertEquals(0x00.toByte(), raw.rgba[2])
        assertEquals(0xFF.toByte(), raw.rgba[3])
    }

    @Test
    fun downscalesToFitPreservingAspectRatio() {
        val raw = CliImageTranscoder.transcode(pngBytes(100, 50, 0xFF00FF00.toInt()), 10, 10)
        assertNotNull(raw)
        assertEquals(10, raw.width)
        assertEquals(5, raw.height)
        assertEquals(10 * 5 * 4, raw.rgba.size)
    }

    @Test
    fun neverUpscalesASmallImage() {
        val raw = CliImageTranscoder.transcode(pngBytes(4, 2, 0xFF0000FF.toInt()), 1000, 1000)
        assertNotNull(raw)
        assertEquals(4, raw.width)
        assertEquals(2, raw.height)
    }

    @Test
    fun returnsStraightAlphaNotPremultiplied() {
        // Half-transparent red: premultiplied storage would read back r=128,
        // straight alpha must restore r=255 (allow rounding wiggle)
        val raw = CliImageTranscoder.transcode(pngBytes(2, 2, 0x80FF0000.toInt()), 100, 100)
        assertNotNull(raw)
        val r = raw.rgba[0].toInt() and 0xFF
        val a = raw.rgba[3].toInt() and 0xFF
        assertTrue(abs(a - 0x80) <= 1, "alpha should stay ~0x80, was $a")
        assertTrue(r >= 0xFD, "red should be un-premultiplied back to ~0xFF, was $r")
    }

    @Test
    fun rejectsUndecodableBytes() {
        assertNull(CliImageTranscoder.transcode("not an image".encodeToByteArray(), 100, 100))
        assertNull(CliImageTranscoder.transcode(ByteArray(0), 100, 100))
    }

    @Test
    fun clampsDegenerateBoxToOnePixel() {
        val raw = CliImageTranscoder.transcode(pngBytes(4, 2, 0xFFFF0000.toInt()), 0, -5)
        assertNotNull(raw)
        assertEquals(1, raw.width)
        assertEquals(1, raw.height)
    }
}
