package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.crosspaste.image.coil.AppSourceItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.large2X
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.AppSourceIcon(
    modifier: Modifier = Modifier,
    size: Dp = large2X,
    defaultIcon: @Composable () -> Unit = {},
) {
    val source = pasteData.source

    if (source == null) {
        defaultIcon()
        return
    }

    val iconStyle = koinInject<IconStyle>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    val density = LocalDensity.current

    val finalSize =
        remember(source, size) {
            if (iconStyle.isMacStyleIcon(source)) {
                val paddingRatio = 0.075f
                val contentRatio = 1f - (paddingRatio * 2)
                size / contentRatio
            } else {
                size
            }
        }

    val sizePx = with(density) { finalSize.roundToPx() }

    val model =
        remember(source, platformContext) {
            ImageRequest
                .Builder(platformContext)
                .data(AppSourceItem(source))
                .size(sizePx)
                .precision(Precision.INEXACT)
                .scale(Scale.FILL)
                .crossfade(false)
                .build()
        }

    SubcomposeAsyncImage(
        modifier = modifier.size(finalSize),
        model = model,
        imageLoader = imageLoaders.appSourceLoader,
        contentDescription = "Paste Icon",
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
