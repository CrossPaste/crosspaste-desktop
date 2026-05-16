package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Broken_image
import com.crosspaste.image.coil.ImageItem
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.tiny
import okio.Path

@Composable
fun BoxScope.StaticImageSidePreview(
    imagePath: Path,
    pasteFileCoordinate: PasteFileCoordinate,
    intSize: IntSize?,
    targetSizePx: Size,
    smartImageDisplayStrategy: SmartImageDisplayStrategy,
    imageLoader: ImageLoader,
    platformContext: PlatformContext,
) {
    val requestSize =
        remember(targetSizePx, intSize) {
            if (intSize != null && intSize.width > 0 && intSize.height > 0) {
                smartImageDisplayStrategy.computeRequestSize(
                    srcSize = Size(intSize.width.toFloat(), intSize.height.toFloat()),
                    dstSize = targetSizePx,
                )
            } else {
                targetSizePx
            }
        }

    val request =
        remember(pasteFileCoordinate, requestSize) {
            ImageRequest
                .Builder(platformContext)
                .data(ImageItem(pasteFileCoordinate, false))
                .size(width = requestSize.width.toInt(), height = requestSize.height.toInt())
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }

    SubcomposeAsyncImage(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        model = request,
        imageLoader = imageLoader,
        contentDescription = "imageType",
        contentScale = ContentScale.Fit,
        content = {
            val state by painter.state.collectAsState()
            when (state) {
                is AsyncImagePainter.State.Loading -> StaticLoadingIndicator()
                is AsyncImagePainter.State.Error -> StaticBrokenIcon(imagePath.name)
                else -> {
                    val intrinsicSize = painter.intrinsicSize
                    val displayResult =
                        smartImageDisplayStrategy.compute(
                            srcSize = Size(intrinsicSize.width, intrinsicSize.height),
                            dstSize = targetSizePx,
                        )
                    TransparentBackground(modifier = Modifier.matchParentSize())
                    SubcomposeAsyncImageContent(
                        contentScale = displayResult.contentScale,
                        alignment = displayResult.alignment,
                    )
                }
            }
        },
    )
}

@Composable
private fun StaticLoadingIndicator() {
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
private fun StaticBrokenIcon(name: String) {
    Icon(
        imageVector = MaterialSymbols.Rounded.Broken_image,
        contentDescription = name,
        modifier = Modifier.size(gigantic),
        tint = MaterialTheme.colorScheme.onBackground,
    )
}
