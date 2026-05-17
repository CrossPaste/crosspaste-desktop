package com.crosspaste.ui.paste.side.preview

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkiaAnimatedImageViewTest {

    @Test
    fun `isAnimatedImage returns true for lowercase gif`() {
        assertTrue("anim.gif".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage is case-insensitive`() {
        assertTrue("anim.GIF".toPath().isAnimatedImage())
        assertTrue("anim.Gif".toPath().isAnimatedImage())
        assertTrue("ANIM.gIf".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage handles nested directories`() {
        assertTrue("/tmp/clips/anim.gif".toPath().isAnimatedImage())
        assertTrue("nested/dir/anim.GIF".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage returns false for non-animated image extensions`() {
        assertFalse("photo.png".toPath().isAnimatedImage())
        assertFalse("photo.jpg".toPath().isAnimatedImage())
        assertFalse("photo.webp".toPath().isAnimatedImage())
        assertFalse("photo.apng".toPath().isAnimatedImage())
        assertFalse("photo.heic".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage returns false for unknown extension`() {
        assertFalse("file.xyz".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage returns false when there is no extension`() {
        assertFalse("README".toPath().isAnimatedImage())
        assertFalse("noext".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage returns false for trailing dot`() {
        // Path.extension is "" when the filename ends with a dot, so this must not match.
        assertFalse("trailing.".toPath().isAnimatedImage())
    }

    @Test
    fun `isAnimatedImage matches only the final extension segment`() {
        // ".gif.bak" has extension "bak", not "gif".
        assertFalse("anim.gif.bak".toPath().isAnimatedImage())
        // ".bak.gif" has extension "gif".
        assertTrue("anim.bak.gif".toPath().isAnimatedImage())
    }

    @Test
    fun `frameDurationMs clamps null duration to the minimum floor`() {
        assertEquals(MIN_FRAME_DURATION_MS, frameDurationMs(null))
    }

    @Test
    fun `frameDurationMs clamps zero to the minimum floor`() {
        // Many GIFs declare 0ms per frame; we must not pass that to delay().
        assertEquals(MIN_FRAME_DURATION_MS, frameDurationMs(0))
    }

    @Test
    fun `frameDurationMs clamps negative duration to the minimum floor`() {
        assertEquals(MIN_FRAME_DURATION_MS, frameDurationMs(-50))
    }

    @Test
    fun `frameDurationMs clamps durations below the floor`() {
        assertEquals(MIN_FRAME_DURATION_MS, frameDurationMs(MIN_FRAME_DURATION_MS - 1))
        assertEquals(MIN_FRAME_DURATION_MS, frameDurationMs(1))
    }

    @Test
    fun `frameDurationMs returns the floor when duration equals it`() {
        assertEquals(MIN_FRAME_DURATION_MS, frameDurationMs(MIN_FRAME_DURATION_MS))
    }

    @Test
    fun `frameDurationMs preserves durations above the floor`() {
        assertEquals(40, frameDurationMs(40))
        assertEquals(100, frameDurationMs(100))
        assertEquals(1_000, frameDurationMs(1_000))
    }
}
