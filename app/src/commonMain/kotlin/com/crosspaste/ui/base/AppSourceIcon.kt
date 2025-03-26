package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
fun AppSourceIcon(
    pasteData: PasteData,
    source: String,
    iconColor: Color,
    size: Dp = 20.dp,
) {
    val iconStyle = koinInject<IconStyle>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    val paddingRatio = 0.075f
    val contentRatio = 1f - (paddingRatio * 2)

    val isMacStyleIcon by remember(source) { mutableStateOf(iconStyle.isMacStyleIcon(source)) }
    val imageSize by remember(source, size, isMacStyleIcon) {
        mutableStateOf(
            if (isMacStyleIcon) size / contentRatio else size,
        )
    }

    SubcomposeAsyncImage(
        modifier = Modifier.size(imageSize),
        model =
            ImageRequest.Builder(platformContext)
                .data(PasteDataItem(pasteData))
                .crossfade(false)
                .build(),
        imageLoader = imageLoaders.appSourceLoader,
        contentDescription = "Paste Icon",
        content = {
            val state = this.painter.state.collectAsState().value
            when (state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    Icon(
                        painter = htmlOrRtf(),
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
