package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.coil.CropTransformation
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.image.coil.PasteDataItem
import org.koin.compose.koinInject

@Composable
fun SideAppSourceIcon(
    modifier: Modifier = Modifier,
    pasteData: PasteData,
    defaultIcon: @Composable () -> Unit = {},
) {
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    SubcomposeAsyncImage(
        modifier = modifier,
        model =
            ImageRequest
                .Builder(platformContext)
                .data(PasteDataItem(pasteData))
                .transformations(CropTransformation())
                .crossfade(false)
                .build(),
        imageLoader = imageLoaders.appSourceLoader,
        contentDescription = "Paste Icon",
        content = {
            val state =
                this.painter.state
                    .collectAsState()
                    .value
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
