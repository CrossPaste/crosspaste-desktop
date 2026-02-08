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
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.image.coil.UrlItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.large2X
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.PasteUrlIcon(size: Dp = large2X) {
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    val urlPasteItem = getPasteItem(UrlPasteItem::class)

    val density = LocalDensity.current

    val sizePx = with(density) { size.roundToPx() }

    val urlItem =
        remember(urlPasteItem.url, pasteData.id) {
            UrlItem(urlPasteItem.url, pasteData.getPasteCoordinate())
        }

    val model =
        remember(urlItem, sizePx) {
            ImageRequest
                .Builder(platformContext)
                .data(urlItem)
                .size(sizePx)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }

    SubcomposeAsyncImage(
        modifier = Modifier.size(size),
        model = model,
        imageLoader = imageLoaders.faviconImageLoader,
        contentDescription = "Paste Icon",
        content = {
            val state by this.painter.state.collectAsState()
            when (state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    LocalThemeExtState.current.urlTypeIconData.IconContent()
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        },
    )
}
