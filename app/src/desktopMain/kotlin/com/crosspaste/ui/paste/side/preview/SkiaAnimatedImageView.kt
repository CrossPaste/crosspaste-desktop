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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Broken_image
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.extension
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
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

private sealed interface CodecState {
    data object Loading : CodecState

    data object Failed : CodecState

    data class Ready(
        val codec: Codec,
    ) : CodecState
}

@Composable
fun SkiaAnimatedImageView(
    path: Path,
    targetSizePx: Size,
    smartImageDisplayStrategy: SmartImageDisplayStrategy,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by produceCodecState(path)

    when (val current = state) {
        CodecState.Loading -> LoadingIndicator()
        CodecState.Failed -> BrokenIcon(path.name)
        is CodecState.Ready ->
            AnimatedFrames(
                codec = current.codec,
                targetSizePx = targetSizePx,
                smartImageDisplayStrategy = smartImageDisplayStrategy,
                isPlaying = isPlaying,
                modifier = modifier,
                contentDescription = path.name,
            )
    }
}

@Composable
private fun AnimatedFrames(
    codec: Codec,
    targetSizePx: Size,
    smartImageDisplayStrategy: SmartImageDisplayStrategy,
    isPlaying: Boolean,
    modifier: Modifier,
    contentDescription: String,
) {
    // Two bitmaps so the next frame can be decoded into the back buffer on IO
    // while the front buffer keeps drawing. The swap publishes on the UI
    // thread once readPixels returns.
    val bitmaps =
        remember(codec) {
            Array(2) { Bitmap().apply { allocPixels(codec.imageInfo) } }
        }

    DisposableEffect(bitmaps) {
        onDispose { bitmaps.forEach { it.close() } }
    }

    val frameInfos = remember(codec) { codec.framesInfo }
    val frameCount = remember(codec) { codec.frameCount.coerceAtLeast(1) }

    var frameIndex by remember(codec) { mutableIntStateOf(0) }
    var frontIndex by remember(codec) { mutableIntStateOf(0) }
    var frameTick by remember(codec) { mutableIntStateOf(0) }

    LaunchedEffect(codec, isPlaying, frameCount) {
        // Prime frame 0 once per codec so something is drawable before the
        // loop runs and so single-frame inputs (frameCount == 1) still render.
        // Skipping on re-launch keeps the current frame visible when
        // isPlaying toggles mid-playback.
        if (frameTick == 0) {
            withContext(ioDispatcher) {
                runCatching { codec.readPixels(bitmaps[0], 0) }
                    .onFailure { logger.warn(it) { "Failed to decode initial frame" } }
            }
            frameTick = 1
        }

        if (!isPlaying || frameCount <= 1) return@LaunchedEffect

        while (true) {
            val durationMs = frameDurationMs(frameInfos.getOrNull(frameIndex)?.duration)
            delay(durationMs.milliseconds)
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
                frameTick++
            }
        }
    }

    val imageBitmap =
        remember(frontIndex, frameTick) {
            bitmaps[frontIndex].asComposeImageBitmap()
        }

    val displayResult =
        remember(codec, targetSizePx) {
            smartImageDisplayStrategy.compute(
                srcSize = Size(codec.imageInfo.width.toFloat(), codec.imageInfo.height.toFloat()),
                dstSize = targetSizePx,
            )
        }

    Image(
        bitmap = imageBitmap,
        contentDescription = contentDescription,
        contentScale = displayResult.contentScale,
        alignment = displayResult.alignment,
        modifier = modifier,
    )
}

@Composable
private fun produceCodecState(path: Path) =
    produceState<CodecState>(initialValue = CodecState.Loading, key1 = path) {
        val loaded =
            withContext(ioDispatcher) {
                runCatching {
                    val bytes = path.toFile().readBytes()
                    CodecState.Ready(Codec.makeFromData(Data.makeFromBytes(bytes)))
                }.onFailure {
                    logger.warn(it) { "Failed to decode animated image: $path" }
                }.getOrElse { CodecState.Failed }
            }
        value = loaded
        awaitDispose {
            if (loaded is CodecState.Ready) loaded.codec.close()
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
