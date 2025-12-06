package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.utils.safeIsDirectory
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.PasteFileIcon(
    iconColor: Color,
    size: Dp = large2X,
) {
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val density = LocalDensity.current

    pasteData.getPasteItem(PasteFiles::class)?.let {
        val paths = it.getFilePaths(userDataPathProvider)
        if (paths.isNotEmpty()) {
            val isSingleFile = paths.size == 1 && !paths[0].safeIsDirectory
            val sizePx = with(density) { size.roundToPx() }
            if (isSingleFile) {
                val model =
                    remember(paths[0], sizePx) {
                        ImageRequest
                            .Builder(platformContext)
                            .data(FileExtItem(paths[0]))
                            .size(sizePx)
                            .precision(Precision.INEXACT)
                            .crossfade(false)
                            .build()
                    }

                SubcomposeAsyncImage(
                    modifier = Modifier.size(size),
                    model = model,
                    imageLoader = imageLoaders.fileExtImageLoader,
                    contentDescription = "Paste Icon",
                    content = {
                        val state by this.painter.state.collectAsState()
                        when (state) {
                            is AsyncImagePainter.State.Loading,
                            is AsyncImagePainter.State.Error,
                            -> {
                                Icon(
                                    painter = file(),
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
            } else {
                Icon(
                    painter = folder(),
                    contentDescription = "folder",
                    modifier = Modifier.size(size),
                    tint = iconColor,
                )
            }
        } else {
            Icon(
                painter = fileSlash(),
                contentDescription = "fileSlash",
                modifier = Modifier.size(size),
                tint = iconColor,
            )
        }
    }
}
