package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Broken_image
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.extension
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

// Many GIFs declare 0ms per frame; browsers clamp to ~100ms. 20ms (~50fps) is
// a conservative floor that still respects intentionally fast animations.
internal const val MIN_FRAME_DURATION_MS = 20

// Extensions whose animation Coil 3 cannot play on desktop, so we route them
// through Skia's Codec instead. Add new entries here when extending support
// (e.g. "apng", "webp" for animated WebP, "heic" for HEIF sequences).
private val animatedImageExtensions: Set<String> = setOf("gif")

fun Path.isAnimatedImage(): Boolean = extension.lowercase() in animatedImageExtensions

internal fun frameDurationMs(rawDurationMs: Int?): Int =
    (rawDurationMs ?: MIN_FRAME_DURATION_MS).coerceAtLeast(MIN_FRAME_DURATION_MS)

// Abstraction over Skia's Codec so [runAnimationLoop] can be exercised in tests
// without a real GIF on disk. Production wires it to [SkiaCodecDecoder].
//
// Contract: decodeFrame must NOT throw — return false on failure. Implementations
// own any native pixel buffers and release them in [close].
internal interface FrameDecoder {
    val frameCount: Int
    val srcSize: Size

    fun frameDelayMs(index: Int): Int

    fun newBuffer(): FrameBuffer

    suspend fun decodeFrame(
        into: FrameBuffer,
        index: Int,
    ): Boolean

    fun close()
}

internal interface FrameBuffer {
    fun asImageBitmap(): ImageBitmap

    fun close()
}

// Regression guard: see commit bb9a3819. readPixels is a synchronous JNI call
// that ignores coroutine cancellation. close() on the codec or any bitmap must
// run only AFTER an in-flight decodeFrame returns, otherwise the JNI call frees
// pixel buffers under its own feet and crashes the process.
//
// This is enforced structurally:
//  - decoder + buffers are owned by THIS suspend function
//  - finally blocks run after the last suspension point returns, so
//    cancellation cannot race with an active decode
//
// Do NOT split decoder/buffer ownership across a sibling DisposableEffect or
// produceState.awaitDispose — those run concurrently with this function's
// withContext(ioDispatcher) and reintroduce the UAF.
internal suspend fun runAnimationLoop(
    decoder: FrameDecoder,
    isPlaying: () -> Boolean,
    awaitPlaying: suspend () -> Unit,
    onReady: (ImageBitmap, Size) -> Unit,
) {
    try {
        val bitmaps = Array(2) { decoder.newBuffer() }
        try {
            // Prime frame 0 before publishing Ready so the first paint is the
            // real first frame, not an empty bitmap.
            decoder.decodeFrame(bitmaps[0], 0)

            var frameIndex = 0
            var frontIndex = 0
            onReady(bitmaps[frontIndex].asImageBitmap(), decoder.srcSize)

            if (decoder.frameCount <= 1) awaitCancellation()

            while (true) {
                if (!isPlaying()) {
                    // Suspend until isPlaying flips back to true instead of
                    // polling, so a hidden preview costs no wake-ups.
                    awaitPlaying()
                    continue
                }
                val durationMs = decoder.frameDelayMs(frameIndex)
                delay(durationMs.milliseconds)
                if (!isPlaying()) continue
                val nextIndex = (frameIndex + 1) % decoder.frameCount
                val backIndex = 1 - frontIndex
                val success = decoder.decodeFrame(bitmaps[backIndex], nextIndex)
                if (success) {
                    frameIndex = nextIndex
                    frontIndex = backIndex
                    onReady(bitmaps[frontIndex].asImageBitmap(), decoder.srcSize)
                }
            }
        } finally {
            bitmaps.forEach { it.close() }
        }
    } finally {
        decoder.close()
    }
}

private sealed interface FrameState {
    data object Loading : FrameState

    data object Failed : FrameState

    data class Ready(
        val imageBitmap: ImageBitmap,
        val srcSize: Size,
    ) : FrameState
}

@Composable
fun SkiaAnimatedImageView(
    path: Path,
    targetSizePx: Size,
    smartImageDisplayStrategy: SmartImageDisplayStrategy,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var frameState by remember(path) { mutableStateOf<FrameState>(FrameState.Loading) }
    val playing by rememberUpdatedState(isPlaying)

    LaunchedEffect(path) {
        val decoder =
            withContext(ioDispatcher) {
                runCatching { SkiaCodecDecoder.load(path) }
                    .onFailure { logger.warn(it) { "Failed to decode animated image: $path" } }
                    .getOrNull()
            }

        if (decoder == null) {
            frameState = FrameState.Failed
            return@LaunchedEffect
        }

        runAnimationLoop(
            decoder = decoder,
            isPlaying = { playing },
            awaitPlaying = { snapshotFlow { playing }.first { it } },
            onReady = { bitmap, size -> frameState = FrameState.Ready(bitmap, size) },
        )
    }

    when (val current = frameState) {
        FrameState.Loading -> LoadingIndicator()
        FrameState.Failed -> BrokenIcon(path.name)
        is FrameState.Ready -> {
            val displayResult =
                remember(current.srcSize, targetSizePx) {
                    smartImageDisplayStrategy.compute(
                        srcSize = current.srcSize,
                        dstSize = targetSizePx,
                    )
                }
            Image(
                bitmap = current.imageBitmap,
                contentDescription = path.name,
                contentScale = displayResult.contentScale,
                alignment = displayResult.alignment,
                modifier = modifier,
            )
        }
    }
}

private class SkiaCodecDecoder(
    private val codec: Codec,
) : FrameDecoder {
    private val framesInfo = codec.framesInfo

    override val frameCount: Int = codec.frameCount.coerceAtLeast(1)

    override val srcSize: Size =
        Size(codec.imageInfo.width.toFloat(), codec.imageInfo.height.toFloat())

    override fun frameDelayMs(index: Int): Int = frameDurationMs(framesInfo.getOrNull(index)?.duration)

    override fun newBuffer(): FrameBuffer = SkiaBitmapBuffer(Bitmap().apply { allocPixels(codec.imageInfo) })

    override suspend fun decodeFrame(
        into: FrameBuffer,
        index: Int,
    ): Boolean =
        withContext(ioDispatcher) {
            require(into is SkiaBitmapBuffer) { "Expected SkiaBitmapBuffer, got ${into::class}" }
            runCatching { codec.readPixels(into.bitmap, index) }
                .onFailure { logger.warn(it) { "Failed to decode frame $index" } }
                .isSuccess
        }

    override fun close() = codec.close()

    companion object {
        fun load(path: Path): SkiaCodecDecoder {
            val bytes = path.toFile().readBytes()
            return SkiaCodecDecoder(Codec.makeFromData(Data.makeFromBytes(bytes)))
        }
    }
}

private class SkiaBitmapBuffer(
    val bitmap: Bitmap,
) : FrameBuffer {
    override fun asImageBitmap(): ImageBitmap = bitmap.asComposeImageBitmap()

    override fun close() = bitmap.close()
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(gigantic),
            strokeWidth = tiny,
        )
    }
}

@Composable
private fun BrokenIcon(name: String) {
    Icon(
        imageVector = MaterialSymbols.Rounded.Broken_image,
        contentDescription = name,
        modifier = Modifier.size(gigantic),
        tint = MaterialTheme.colorScheme.onBackground,
    )
}
