package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.image.coil.PasteDataItem
import org.koin.compose.koinInject

@Composable
fun PasteUrlIcon(
    pasteData: PasteData,
    iconColor: Color,
    size: Dp = 20.dp,
) {
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    SubcomposeAsyncImage(
        modifier = Modifier.size(size),
        model =
            ImageRequest.Builder(platformContext)
                .data(PasteDataItem(pasteData))
                .crossfade(false)
                .build(),
        imageLoader = imageLoaders.faviconImageLoader,
        contentDescription = "Paste Icon",
        content = {
            when (this.painter.state.collectAsState().value) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    Icon(
                        painter = link(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.size(size),
                        tint = iconColor,
                    )
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        },
    )
}
