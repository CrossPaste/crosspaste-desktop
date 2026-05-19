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

    // Codec + double-buffered bitmaps are owned by this single LaunchedEffect
    // so the finally block runs only after any in-flight readPixels returns.
    // readPixels is a synchronous JNI call that ignores coroutine cancellation,
    // so releasing native resources from a sibling DisposableEffect.onDispose
    // (or produceState's awaitDispose) could free pixel buffers under an
    // active decode and crash with SIGSEGV.
    LaunchedEffect(path) {
        val codec =
            withContext(ioDispatcher) {
                runCatching {
                    val bytes = path.toFile().readBytes()
                    Codec.makeFromData(Data.makeFromBytes(bytes))
                }.onFailure {
                    logger.warn(it) { "Failed to decode animated image: $path" }
                }.getOrNull()
            }

        if (codec == null) {
            frameState = FrameState.Failed
            return@LaunchedEffect
        }

        try {
            val bitmaps = Array(2) { Bitmap().apply { allocPixels(codec.imageInfo) } }
            try {
                val frameInfos = codec.framesInfo
                val frameCount = codec.frameCount.coerceAtLeast(1)
                val srcSize = Size(codec.imageInfo.width.toFloat(), codec.imageInfo.height.toFloat())

                // Prime frame 0 before publishing Ready so the first paint is the
                // real first frame, not an empty bitmap.
                withContext(ioDispatcher) {
                    runCatching { codec.readPixels(bitmaps[0], 0) }
                        .onFailure { logger.warn(it) { "Failed to decode initial frame" } }
                }

                var frameIndex = 0
                var frontIndex = 0
                frameState = FrameState.Ready(bitmaps[frontIndex].asComposeImageBitmap(), srcSize)

                if (frameCount <= 1) awaitCancellation()

                while (true) {
                    if (!playing) {
                        // Suspend until isPlaying flips back to true instead of
                        // polling, so a hidden preview costs no wake-ups.
                        snapshotFlow { playing }.first { it }
                        continue
                    }
                    val durationMs = frameDurationMs(frameInfos.getOrNull(frameIndex)?.duration)
                    delay(durationMs.milliseconds)
                    if (!playing) continue
                    val nextIndex = (frameIndex + 1) % frameCount
                    val backIndex = 1 - frontIndex
                    // Decode off the UI thread; LZW decompression on large frames
                    // can otherwise eat the next render window.
                    val success =
                        withContext(ioDispatcher) {
                            runCatching { codec.readPixels(bitmaps[backIndex], nextIndex) }
                                .onFailure { logger.warn(it) { "Failed to decode frame $nextIndex" } }
                                .isSuccess
                        }
                    if (success) {
                        frameIndex = nextIndex
                        frontIndex = backIndex
                        frameState = FrameState.Ready(bitmaps[frontIndex].asComposeImageBitmap(), srcSize)
                    }
                }
            } finally {
                bitmaps.forEach { it.close() }
            }
        } finally {
            codec.close()
        }
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
