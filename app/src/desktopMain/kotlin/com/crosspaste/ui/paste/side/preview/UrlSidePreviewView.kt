package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import coil3.size.Precision
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.image.coil.GenerateImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.PasteUrlIcon
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.paste.preview.UrlBottomSolid
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.DesktopAppUIFont
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.UrlSidePreviewView() {
    val appSize = koinInject<DesktopAppSize>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val smartImageDisplayStrategy = remember { SmartImageDisplayStrategy() }

    val urlPasteItem = getPasteItem(UrlPasteItem::class)

    val openGraphPath =
        remember(urlPasteItem, pasteData.id) {
            urlPasteItem.getRenderingFilePath(
                pasteData.getPasteCoordinate(),
                userDataPathProvider,
            )
        }

    val density = LocalDensity.current
    val targetUiSize = appSize.sidePasteContentSize
    val targetSizePx =
        with(density) {
            Size(
                targetUiSize.width.toPx(),
                (targetUiSize.height - huge).toPx().coerceAtLeast(1f),
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
                titleStyle =
                    DesktopAppUIFont.sideUrlTitleTextStyle.copy(
                        color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.topBackground),
                    ),
                title = urlPasteItem.getTitle(),
                url = urlPasteItem.url,
                maxLines = 2,
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = huge),
            contentAlignment = Alignment.Center,
        ) {
            val request =
                remember(platformContext, openGraphPath, targetSizePx) {
                    ImageRequest
                        .Builder(platformContext)
                        .data(GenerateImageItem(openGraphPath))
                        .size(width = targetSizePx.width.toInt(), height = targetSizePx.height.toInt())
                        .precision(Precision.INEXACT)
                        .crossfade(true)
                        .build()
                }

            SubcomposeAsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = request,
                imageLoader = imageLoaders.generateImageLoader,
                contentDescription = "url preview",
                contentScale = ContentScale.Fit,
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
                            val intrinsicSize = painter.intrinsicSize

                            val displayResult =
                                smartImageDisplayStrategy.compute(
                                    srcSize = Size(intrinsicSize.width, intrinsicSize.height),
                                    dstSize = targetSizePx,
                                )

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
