package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.utils.safeIsDirectory
import org.koin.compose.koinInject

@Composable
fun PasteFileIcon(
    pasteData: PasteData,
    iconColor: Color,
    size: Dp = large2X,
) {
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    pasteData.getPasteItem(PasteFiles::class)?.let {
        val paths = it.getFilePaths(userDataPathProvider)
        if (paths.isNotEmpty()) {
            val isSingleFile = paths.size == 1 && !paths[0].safeIsDirectory
            if (isSingleFile) {
                SubcomposeAsyncImage(
                    modifier = Modifier.size(size),
                    model =
                        ImageRequest
                            .Builder(platformContext)
                            .data(FileExtItem(paths[0]))
                            .crossfade(false)
                            .build(),
                    imageLoader = imageLoaders.fileExtImageLoader,
                    contentDescription = "Paste Icon",
                    content = {
                        when (
                            this.painter.state
                                .collectAsState()
                                .value
                        ) {
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
