package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import com.crosspaste.image.coil.AppSourceIconTransformer
import com.crosspaste.image.coil.AppSourceItem
import com.crosspaste.image.coil.ImageLoaderQualifiers
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.paste.PasteDataScope
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.SideAppSourceIcon(
    modifier: Modifier = Modifier,
    defaultIcon: @Composable () -> Unit = {},
) {
    val appSourceLoader = koinInject<ImageLoader>(qualifier = ImageLoaderQualifiers.APP_SOURCE)
    val transformer = koinInject<AppSourceIconTransformer>()
    val platformContext = koinInject<PlatformContext>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val density = LocalDensity.current

    val sizePx = with(density) { appSizeValue.sideTitleHeight.roundToPx() }
    val requestSizePx = transformer.requestSize(sizePx)

    val model =
        remember(pasteData.source, pasteData.appInstanceId, requestSizePx) {
            ImageRequest
                .Builder(platformContext)
                .data(AppSourceItem(pasteData.source, pasteData.appInstanceId))
                .transformations(transformer.transformations)
                .size(requestSizePx)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }

    SubcomposeAsyncImage(
        modifier = modifier,
        model = model,
        imageLoader = appSourceLoader,
        contentDescription = pasteData.source,
        content = {
            val state by this.painter.state.collectAsState()
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
        },
    )
}
