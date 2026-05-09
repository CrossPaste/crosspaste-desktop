package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Play_arrow
import com.crosspaste.image.VideoThumbnailLoader
import com.crosspaste.image.coil.ImageLoaderQualifiers
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.SingleFileIcon
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import okio.Path
import org.koin.compose.koinInject

@Composable
fun VideoFilePreviewContent(
    videoPath: Path,
    pasteFileCoordinate: PasteFileCoordinate,
) {
    val videoThumbnailLoader = koinInject<VideoThumbnailLoader>()
    val userImageLoader = koinInject<ImageLoader>(qualifier = ImageLoaderQualifiers.USER_IMAGE)
    val platformContext = koinInject<PlatformContext>()

    val smartImageDisplayStrategy = remember { SmartImageDisplayStrategy() }

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val density = LocalDensity.current
    val targetUiSize = appSizeValue.sidePasteContentSize
    val targetSizePx =
        with(density) {
            Size(
                targetUiSize.width.toPx(),
                (targetUiSize.height - huge).toPx().coerceAtLeast(1f),
            )
        }

    val thumbnailPath by produceState<Path?>(
        initialValue = null,
        key1 = pasteFileCoordinate,
    ) {
        value =
            withContext(ioDispatcher) {
                videoThumbnailLoader.load(pasteFileCoordinate)
            }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val resolved = thumbnailPath
        if (resolved != null) {
            val request =
                remember(platformContext, resolved, targetSizePx) {
                    ImageRequest
                        .Builder(platformContext)
                        .data(resolved.toNioPath().toFile())
                        .size(width = targetSizePx.width.toInt(), height = targetSizePx.height.toInt())
                        .precision(Precision.INEXACT)
                        .crossfade(true)
                        .build()
                }

            SubcomposeAsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = request,
                imageLoader = userImageLoader,
                contentDescription = "video thumbnail",
                content = {
                    val state by painter.state.collectAsState()
                    when (state) {
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Error,
                        -> {
                            SingleFileIcon(
                                filePath = videoPath,
                                size = AppUISize.massive,
                            )
                        }
                        else -> {
                            val intrinsicSize = painter.intrinsicSize
                            val displayResult =
                                smartImageDisplayStrategy.compute(
                                    srcSize = Size(intrinsicSize.width, intrinsicSize.height),
                                    dstSize = targetSizePx,
                                )
                            SubcomposeAsyncImageContent(
                                contentScale = displayResult.contentScale,
                                alignment = displayResult.alignment,
                            )
                        }
                    }
                },
            )
        } else {
            SingleFileIcon(
                filePath = videoPath,
                size = AppUISize.massive,
            )
        }

        Box(
            modifier =
                Modifier
                    .size(huge)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Play_arrow,
                contentDescription = "play",
                tint = Color.White,
                modifier = Modifier.size(xxxxLarge),
            )
        }
    }
}
