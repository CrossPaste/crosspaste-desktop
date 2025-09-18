package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
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
import com.crosspaste.image.coil.GenerateImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.rendering.RenderingHelper
import com.crosspaste.ui.base.ImageDisplayStrategy
import com.crosspaste.ui.base.PasteUrlIcon
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.UrlSidePreviewView() {
    getPasteItem(UrlPasteItem::class).let { urlPasteItem ->
        val appSize = koinInject<DesktopAppSize>()
        val imageLoaders = koinInject<ImageLoaders>()
        val platformContext = koinInject<PlatformContext>()
        val renderingHelper = koinInject<RenderingHelper>()
        val smartImageDisplayStrategy = SmartImageDisplayStrategy()
        val userDataPathProvider = koinInject<UserDataPathProvider>()

        val openGraphPath =
            urlPasteItem.getRenderingFilePath(
                pasteData.getPasteCoordinate(),
                userDataPathProvider,
            )

        var displayResult by remember {
            mutableStateOf(
                ImageDisplayStrategy.DisplayResult(
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                ),
            )
        }

        SidePasteLayoutView(
            pasteBottomContent = {
                UrlBottomSolid(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(huge)
                            .background(AppUIColors.topBackground)
                            .padding(small3X),
                    title = urlPasteItem.getTitle(),
                    url = urlPasteItem.url,
                    maxLines = 2,
                )
            },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = huge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SubcomposeAsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model =
                        ImageRequest
                            .Builder(platformContext)
                            .data(GenerateImageItem(openGraphPath, false, renderingHelper.scale))
                            .crossfade(true)
                            .build(),
                    imageLoader = imageLoaders.generateImageLoader,
                    contentDescription = "url preview",
                    contentScale = displayResult.contentScale,
                    alignment = displayResult.alignment,
                    content = {
                        val state by painter.state.collectAsState()
                        when (state) {
                            is AsyncImagePainter.State.Loading,
                            is AsyncImagePainter.State.Error,
                            -> {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PasteUrlIcon(
                                        iconColor =
                                            MaterialTheme.colorScheme.contentColorFor(
                                                AppUIColors.pasteBackground,
                                            ),
                                        size = xxxxLarge,
                                    )
                                }
                            }

                            else -> {
                                val dstSize =
                                    with(LocalDensity.current) {
                                        Size(
                                            appSize.sidePasteContentSize.width.toPx(),
                                            (appSize.sidePasteContentSize.height - huge).toPx(),
                                        )
                                    }

                                val intrinsicSize = painter.intrinsicSize

                                val srcSize =
                                    Size(
                                        intrinsicSize.width,
                                        intrinsicSize.height,
                                    )

                                displayResult = smartImageDisplayStrategy.compute(srcSize, dstSize)

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
