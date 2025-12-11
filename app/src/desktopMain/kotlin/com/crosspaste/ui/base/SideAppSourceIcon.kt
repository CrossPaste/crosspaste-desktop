package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import com.crosspaste.image.coil.AppSourceItem
import com.crosspaste.image.coil.CropTransformationFactory.create
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.platform.Platform
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalDesktopAppSizeValueState
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

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val density = LocalDensity.current

    val syncPlatform by remember(pasteData.appInstanceId) {
        mutableStateOf(syncManager.getSyncPlatform(pasteData.appInstanceId))
    }

    val sizePx = with(density) { appSizeValue.sideTitleHeight.roundToPx() }

    val model =
        remember(pasteData.source, sizePx, platform, syncPlatform) {
            val transformation = create(platform, syncPlatform)

            val requestSizePx = transformation?.requestSize(sizePx) ?: sizePx

            val transformations = transformation?.let { listOf(it) } ?: listOf()

            ImageRequest
                .Builder(platformContext)
                .data(AppSourceItem(pasteData.source))
                .transformations(transformations)
                .size(requestSizePx)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }

    SubcomposeAsyncImage(
        modifier = modifier,
        model = model,
        imageLoader = imageLoaders.appSourceLoader,
        contentDescription = "Paste Icon",
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
