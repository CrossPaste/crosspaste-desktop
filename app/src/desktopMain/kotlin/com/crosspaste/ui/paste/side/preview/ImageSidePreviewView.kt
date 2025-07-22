package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.coil.ImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteImages
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.ImageDisplayStrategy
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
fun ImageSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteImages::class)?.let { pasteImages ->
        if (pasteImages.count > 0) {
            val appSize = koinInject<DesktopAppSize>()
            val imageLoaders = koinInject<ImageLoaders>()
            val platformContext = koinInject<PlatformContext>()
            val smartImageDisplayStrategy = SmartImageDisplayStrategy()
            val userDataPathProvider = koinInject<UserDataPathProvider>()

            var index by remember(pasteData.id) { mutableStateOf(0) }

            val imagePath = pasteImages.getFilePaths(userDataPathProvider)[index]

            val pasteFileCoordinate = PasteFileCoordinate(pasteData.getPasteCoordinate(), imagePath)

            var displayResult by remember {
                mutableStateOf(
                    ImageDisplayStrategy.DisplayResult(
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                    ),
                )
            }

            SidePasteLayoutView(
                pasteData = pasteData,
                pasteBottomContent = {},
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.wrapContentSize()) {
                        SubcomposeAsyncImage(
                            modifier = Modifier.wrapContentSize(),
                            model =
                                ImageRequest
                                    .Builder(platformContext)
                                    .data(ImageItem(pasteFileCoordinate, false))
                                    .crossfade(true)
                                    .build(),
                            imageLoader = imageLoaders.userImageLoader,
                            contentDescription = "imageType",
                            contentScale = displayResult.contentScale,
                            alignment = displayResult.alignment,
                            content = {
                                val state = painter.state.collectAsState().value
                                when (state) {
                                    is AsyncImagePainter.State.Loading -> {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(gigantic),
                                                strokeWidth = tiny,
                                            )
                                        }
                                    }

                                    is AsyncImagePainter.State.Error -> {
                                        Icon(
                                            painter = imageSlash(),
                                            contentDescription = imagePath.name,
                                            modifier = Modifier.size(gigantic),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }

                                    else -> {
                                        val dstSize =
                                            with(LocalDensity.current) {
                                                Size(
                                                    appSize.sidePasteContentSize.width.toPx(),
                                                    appSize.sidePasteContentSize.height.toPx(),
                                                )
                                            }

                                        val intrinsicSize = painter.intrinsicSize

                                        val srcSize =
                                            Size(
                                                intrinsicSize.width,
                                                intrinsicSize.height,
                                            )

                                        displayResult = smartImageDisplayStrategy.compute(srcSize, dstSize)

                                        TransparentBackground(modifier = Modifier.matchParentSize())

                                        SubcomposeAsyncImageContent(
                                            contentScale = displayResult.contentScale,
                                            alignment = displayResult.alignment,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
