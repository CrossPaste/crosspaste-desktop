package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.crosspaste.image.coil.CropTransformation
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.image.coil.PasteDataItem
import com.crosspaste.platform.Platform
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.paste.PasteDataScope
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.SideAppSourceIcon(
    modifier: Modifier = Modifier,
    defaultIcon: @Composable () -> Unit = {},
) {
    val syncManager = koinInject<SyncManager>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platform = koinInject<Platform>()
    val platformContext = koinInject<PlatformContext>()

    val syncPlatform by remember(pasteData.appInstanceId) {
        mutableStateOf(syncManager.getSyncPlatform(pasteData.appInstanceId))
    }

    SubcomposeAsyncImage(
        modifier = modifier,
        model =
            ImageRequest
                .Builder(platformContext)
                .data(PasteDataItem(pasteData))
                .transformations(CropTransformation(platform, syncPlatform))
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
