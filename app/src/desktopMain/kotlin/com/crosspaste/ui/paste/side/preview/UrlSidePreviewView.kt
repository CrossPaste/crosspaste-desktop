package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.coil.GenerateImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.rendering.RenderingHelper
import com.crosspaste.ui.base.PasteUrlIcon
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun UrlSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(UrlPasteItem::class)?.let { urlPasteItem ->
        val imageLoaders = koinInject<ImageLoaders>()
        val platformContext = koinInject<PlatformContext>()
        val renderingHelper = koinInject<RenderingHelper>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()

        val openGraphPath =
            urlPasteItem.getRenderingFilePath(
                pasteData.getPasteCoordinate(),
                userDataPathProvider,
            )

        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {
                UrlBottomSolid(
                    title = urlPasteItem.getTitle(),
                    url = urlPasteItem.url,
                )
            },
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(bottom = huge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SubcomposeAsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model =
                        ImageRequest.Builder(platformContext)
                            .data(GenerateImageItem(openGraphPath, false, renderingHelper.scale))
                            .crossfade(true)
                            .build(),
                    imageLoader = imageLoaders.generateImageLoader,
                    contentDescription = "url preview",
                    contentScale = ContentScale.FillBounds,
                    content = {
                        val state = painter.state.collectAsState().value
                        when (state) {
                            is AsyncImagePainter.State.Loading,
                            is AsyncImagePainter.State.Error,
                            -> {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PasteUrlIcon(
                                        pasteData = pasteData,
                                        iconColor =
                                            MaterialTheme.colorScheme.contentColorFor(
                                                AppUIColors.pasteBackground,
                                            ),
                                        size = xxxxLarge,
                                    )
                                }
                            }

                            else -> {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    },
                )
            }
        }
    }
}
