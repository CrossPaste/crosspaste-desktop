package com.crosspaste.ui.base

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.image.ImageData
import com.crosspaste.image.ImageDataLoader
import okio.Path
import org.koin.compose.koinInject

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
    val imageDataLoader = koinInject<ImageDataLoader>()
    val density = LocalDensity.current
    AsyncView(
        key = path,
        load = {
            imageDataLoader.loadImageData(path, density)
        },
        loadFor = { loadData ->
            if (loadData.isSuccess() && loadData is ImageData<*>) {
                if (isMacStyleIcon) {
                    Image(
                        painter = loadData.readPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.size(size),
                    )
                } else {
                    val mainSize = (size / 24) * 20
                    val paddingSize = (size / 24) * 2
                    Image(
                        painter = loadData.readPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(paddingSize).size(mainSize),
                    )
                }
            } else if (loadData.isError()) {
                val mainSize = (size / 24) * 20
                val paddingSize = (size / 24) * 2
                Icon(
                    painter = imageSlash(),
                    contentDescription = "Paste Icon",
                    modifier = Modifier.padding(paddingSize).size(mainSize),
                )
            }
        },
    )
}
