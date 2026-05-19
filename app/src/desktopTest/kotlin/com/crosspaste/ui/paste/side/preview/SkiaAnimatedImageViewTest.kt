package com.crosspaste.ui.paste.side.preview

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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

    // --- runAnimationLoop close-ordering regression (commit bb9a3819) ---
    //
    // The production bug: codec.readPixels is a synchronous JNI call that
    // ignores coroutine cancellation. When buffer/codec close used to live in
    // a sibling DisposableEffect.onDispose or produceState.awaitDispose, those
    // could free pixel buffers while readPixels was still touching them
    // (SIGSEGV). bb9a3819 folded ownership into the LaunchedEffect so the
    // finally blocks run only after the in-flight decode returns.
    //
    // The fake decoder simulates JNI behavior with withContext(NonCancellable)
    // around a CompletableDeferred — cancellation cannot pre-empt it.

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `runAnimationLoop close-ordering survives cancel during in-flight decode`() =
        runTest(UnconfinedTestDispatcher()) {
            val events = mutableListOf<String>()
            val decodeStarted = CompletableDeferred<Unit>()
            val decodeUnblock = CompletableDeferred<Unit>()
            val decoder =
                FakeFrameDecoder(
                    events = events,
                    frameCount = 4,
                    blockingFrames =
                        mapOf(
                            0 to BlockingFrame(decodeStarted, decodeUnblock),
                        ),
                )

            val job =
                launch {
                    runAnimationLoop(
                        decoder = decoder,
                        isPlaying = { true },
                        awaitPlaying = { },
                        onReady = { _, _ -> },
                    )
                }

            // Frame 0 priming entered the simulated JNI section.
            decodeStarted.await()
            assertTrue(
                "decode.start(0)" in events,
                "decode start was not recorded: $events",
            )

            // Request cancellation while the decode is still in flight. The
            // structural guarantee under test: nothing close()s yet.
            job.cancel()
            yield()
            yield()
            assertFalse(
                "decode.end(0)" in events,
                "decode returned before we unblocked it; test setup broken: $events",
            )
            assertFalse(
                "buffer.close" in events,
                "buffer.close ran during in-flight decode — UAF reintroduced (events=$events)",
            )
            assertFalse(
                "decoder.close" in events,
                "decoder.close ran during in-flight decode — UAF reintroduced (events=$events)",
            )

            // Release the simulated JNI call; finally blocks should now run.
            decodeUnblock.complete(Unit)
            job.join()

            val decodeEndIdx = events.indexOf("decode.end(0)")
            assertTrue(decodeEndIdx >= 0, "decode.end(0) never observed: $events")
            events.forEachIndexed { i, event ->
                if (event == "buffer.close" || event == "decoder.close") {
                    assertTrue(
                        i > decodeEndIdx,
                        "$event at $i must follow decode.end(0) at $decodeEndIdx (events=$events)",
                    )
                }
            }

            // Both back/front buffers closed exactly once.
            assertEquals(
                2,
                events.count { it == "buffer.close" },
                "expected both bitmap buffers closed (events=$events)",
            )
            // Decoder closes exactly once, AND last — the outer finally runs
            // after the inner finally that closes the bitmaps.
            assertEquals(
                1,
                events.count { it == "decoder.close" },
                "expected decoder closed once (events=$events)",
            )
            assertEquals(
                "decoder.close",
                events.last(),
                "decoder.close must be the final event so it follows bitmap close (events=$events)",
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `runAnimationLoop closes resources cleanly on normal cancel`() =
        runTest(UnconfinedTestDispatcher()) {
            val events = mutableListOf<String>()
            // frameCount == 1 → loop awaitCancellation()s after priming
            val decoder = FakeFrameDecoder(events = events, frameCount = 1)
            var readyCount = 0

            val job =
                launch {
                    runAnimationLoop(
                        decoder = decoder,
                        isPlaying = { true },
                        awaitPlaying = { },
                        onReady = { _, _ -> readyCount++ },
                    )
                }

            // Priming decode + onReady run eagerly under UnconfinedTestDispatcher
            // before the loop reaches awaitCancellation.
            yield()

            job.cancelAndJoin()

            assertEquals(1, readyCount, "single-frame path should emit onReady once")
            assertEquals(2, events.count { it == "buffer.close" })
            assertEquals(1, events.count { it == "decoder.close" })
            assertEquals(
                "decoder.close",
                events.last(),
                "decoder.close must run after both bitmap closes (events=$events)",
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `runAnimationLoop close-ordering survives cancel during mid-loop decode`() =
        runTest(UnconfinedTestDispatcher()) {
            val events = mutableListOf<String>()
            val decode1Started = CompletableDeferred<Unit>()
            val decode1Unblock = CompletableDeferred<Unit>()
            val decoder =
                FakeFrameDecoder(
                    events = events,
                    frameCount = 3,
                    blockingFrames =
                        mapOf(
                            // Frame 1 is the first frame decoded inside the while loop;
                            // priming uses frame 0 which we let return immediately.
                            1 to BlockingFrame(decode1Started, decode1Unblock),
                        ),
                )

            val job =
                launch {
                    runAnimationLoop(
                        decoder = decoder,
                        isPlaying = { true },
                        awaitPlaying = { },
                        onReady = { _, _ -> },
                    )
                }

            // Advance virtual time so the priming decode finishes, the loop
            // delays MIN_FRAME_DURATION_MS, then enters decodeFrame(_, 1).
            decode1Started.await()
            job.cancel()
            yield()
            yield()
            assertFalse(
                "buffer.close" in events,
                "buffer.close fired mid-decode (events=$events)",
            )
            assertFalse(
                "decoder.close" in events,
                "decoder.close fired mid-decode (events=$events)",
            )

            decode1Unblock.complete(Unit)
            job.join()

            val decode1EndIdx = events.indexOf("decode.end(1)")
            assertTrue(decode1EndIdx >= 0, "decode.end(1) never observed: $events")
            events.forEachIndexed { i, event ->
                if (event == "buffer.close" || event == "decoder.close") {
                    assertTrue(
                        i > decode1EndIdx,
                        "$event at $i must follow decode.end(1) at $decode1EndIdx (events=$events)",
                    )
                }
            }
            assertEquals(
                "decoder.close",
                events.last(),
                "decoder.close must be the final event (events=$events)",
            )
        }
}

private data class BlockingFrame(
    val started: CompletableDeferred<Unit>,
    val unblock: CompletableDeferred<Unit>,
)

private class FakeFrameDecoder(
    private val events: MutableList<String>,
    override val frameCount: Int,
    private val blockingFrames: Map<Int, BlockingFrame> = emptyMap(),
) : FrameDecoder {
    override val srcSize: Size = Size(1f, 1f)

    override fun frameDelayMs(index: Int): Int = MIN_FRAME_DURATION_MS

    override fun newBuffer(): FrameBuffer = FakeFrameBuffer(events)

    override suspend fun decodeFrame(
        into: FrameBuffer,
        index: Int,
    ): Boolean {
        events += "decode.start($index)"
        blockingFrames[index]?.let { hook ->
            // Simulate readPixels: a synchronous JNI call that ignores coroutine
            // cancellation. NonCancellable prevents the await() from being
            // pre-empted, reproducing the "must finish before cancellation
            // propagates" property that the bb9a3819 fix relies on.
            withContext(NonCancellable) {
                hook.started.complete(Unit)
                hook.unblock.await()
            }
        }
        events += "decode.end($index)"
        return true
    }

    override fun close() {
        events += "decoder.close"
    }
}

private class FakeFrameBuffer(
    private val events: MutableList<String>,
) : FrameBuffer {
    private val image: ImageBitmap = mockk(relaxed = true)

    override fun asImageBitmap(): ImageBitmap = image

    override fun close() {
        events += "buffer.close"
    }
}
