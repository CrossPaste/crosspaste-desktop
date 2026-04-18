package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import com.crosspaste.image.coil.AppSourceIconTransformer
import com.crosspaste.image.coil.AppSourceItem
import com.crosspaste.image.coil.ImageLoaderQualifiers
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.large2X
import org.koin.compose.koinInject

@Composable
fun AppSourceIcon(
    source: String,
    appInstanceId: String,
    modifier: Modifier = Modifier,
    size: Dp = large2X,
    defaultIcon: @Composable () -> Unit = {},
) {
    val appSourceLoader = koinInject<ImageLoader>(qualifier = ImageLoaderQualifiers.APP_SOURCE)
    val transformer = koinInject<AppSourceIconTransformer>()
    val platformContext = koinInject<PlatformContext>()
    val density = LocalDensity.current

    val sizePx = with(density) { size.roundToPx() }
    val requestSizePx = transformer.requestSize(sizePx)

    val model =
        remember(source, appInstanceId, platformContext, requestSizePx, transformer) {
            ImageRequest
                .Builder(platformContext)
                .data(AppSourceItem(source, appInstanceId))
                .transformations(transformer.transformations)
                .size(requestSizePx)
                .precision(Precision.INEXACT)
                .scale(Scale.FILL)
                .crossfade(true)
                .build()
        }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = model,
            imageLoader = appSourceLoader,
            contentDescription = "App Source Icon",
            contentScale = ContentScale.Fit,
        ) {
            val state by painter.state.collectAsState()
            when (state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    defaultIcon()
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
fun PasteDataScope.AppSourceIcon(
    modifier: Modifier = Modifier,
    size: Dp = large2X,
    defaultIcon: @Composable () -> Unit = {},
) {
    val source = pasteData.source
    if (source == null) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            defaultIcon()
        }
    } else {
        AppSourceIcon(
            source = source,
            appInstanceId = pasteData.appInstanceId,
            modifier = modifier,
            size = size,
            defaultIcon = defaultIcon,
        )
    }
}
