package com.crosspaste.ui.base

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import okio.Path

@Composable
fun PasteIconButton(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val iconButtonSize = size + 4.dp

    Box(
        modifier = modifier.size(iconButtonSize),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier.size(iconButtonSize),
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides
                        if (enabled) {
                            LocalContentColor.current
                        } else {
                            LocalContentColor.current.copy(alpha = 0.38f)
                        },
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AppImageIcon(
    path: Path,
    isMacStyleIcon: Boolean,
    size: Dp = 24.dp,
) {
    var imageSize by remember(path) { mutableStateOf(if (isMacStyleIcon) size else (size / 24) * 20) }
    var imagePaddingSize by remember(path) { mutableStateOf(if (isMacStyleIcon) 0.dp else (size / 24) * 2) }

    SubcomposeAsyncImage(
        modifier = Modifier.padding(imagePaddingSize).size(imageSize),
        model =
            ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(path)
                .crossfade(false)
                .build(),
        contentDescription = "Paste Icon",
        content = {
            when (this.painter.state.collectAsState().value) {
                is AsyncImagePainter.State.Error -> {
                    imageSize = (size / 24) * 20
                    imagePaddingSize = (size / 24) * 2
                    Icon(
                        painter = imageSlash(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(imagePaddingSize).size(imageSize),
                    )
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        },
    )
}
