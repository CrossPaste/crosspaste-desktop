package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.crosspaste.app.AppName
import com.crosspaste.image.coil.AppSourceIconCropTransformation
import com.crosspaste.image.coil.AppSourceItem
import com.crosspaste.image.coil.ImageLoaderQualifiers
import com.crosspaste.image.coil.SideAppSourceIconTransformer
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.paste.PasteDataScope
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.SideAppSourceIcon(
    modifier: Modifier = Modifier,
    defaultIcon: @Composable () -> Unit = {},
) {
    val appSizeValue = LocalDesktopAppSizeValueState.current

    if (pasteData.source == AppName) {
        CroppedCrossPasteLogo(height = appSizeValue.sideTitleHeight, modifier = modifier)
        return
    }

    val appSourceLoader = koinInject<ImageLoader>(qualifier = ImageLoaderQualifiers.APP_SOURCE)
    val platformContext = koinInject<PlatformContext>()

    val density = LocalDensity.current

    val sizePx = with(density) { appSizeValue.sideTitleHeight.roundToPx() }
    val requestSizePx = SideAppSourceIconTransformer.requestSize(sizePx)

    val model =
        remember(pasteData.source, pasteData.appInstanceId, requestSizePx) {
            ImageRequest
                .Builder(platformContext)
                .data(AppSourceItem(pasteData.source, pasteData.appInstanceId))
                .transformations(SideAppSourceIconTransformer.transformations)
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

/**
 * The CrossPaste logo framed the way [AppSourceIconCropTransformation] frames a
 * loaded app icon, so it lines up with the other side title icons.
 */
@Composable
private fun CroppedCrossPasteLogo(
    height: Dp,
    modifier: Modifier,
) {
    val crop = AppSourceIconCropTransformation
    val tileSize = height / (1f - crop.CROP_TOP - crop.CROP_BOTTOM)
    Box(
        modifier =
            modifier
                .height(height)
                .width(tileSize * (1f - crop.CROP_LEFT - crop.CROP_RIGHT))
                .clipToBounds(),
    ) {
        CrossPasteLogo(
            size = tileSize,
            modifier =
                Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .offset(x = -tileSize * crop.CROP_LEFT, y = -tileSize * crop.CROP_TOP),
        )
    }
}
