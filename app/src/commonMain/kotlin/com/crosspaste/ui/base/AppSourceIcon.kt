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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
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

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (source == null) {
            defaultIcon()
            return@Box
        }

        val iconStyle = koinInject<IconStyle>()
        val imageLoaders = koinInject<ImageLoaders>()
        val platformContext = koinInject<PlatformContext>()
        val density = LocalDensity.current

        val visualScale =
            remember(source) {
                if (iconStyle.isMacStyleIcon(source)) {
                    val paddingRatio = 0.075f
                    val contentRatio = 1f - (paddingRatio * 2)
                    1f / contentRatio
                } else {
                    1f
                }
            }

        val sizePx = with(density) { size.roundToPx() }

        val model =
            remember(source, platformContext, sizePx) {
                ImageRequest
                    .Builder(platformContext)
                    .data(AppSourceItem(source))
                    .size(sizePx)
                    .precision(Precision.INEXACT)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build()
            }

        SubcomposeAsyncImage(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scale(visualScale),
            model = model,
            imageLoader = imageLoaders.appSourceLoader,
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
