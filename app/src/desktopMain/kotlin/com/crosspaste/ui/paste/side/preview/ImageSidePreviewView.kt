package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.crosspaste.image.ImageHandler
import com.crosspaste.image.coil.ImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteImages
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.ImageFileFormat
import com.crosspaste.ui.base.ImageFileSize
import com.crosspaste.ui.base.ImageResolution
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject
import java.awt.image.BufferedImage

@Composable
fun PasteDataScope.ImageSidePreviewView() {
    val imageLoaders = koinInject<ImageLoaders>()
    val imageHandler = koinInject<ImageHandler<BufferedImage>>()
    val platformContext = koinInject<PlatformContext>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val fileUtils = remember { getFileUtils() }

    val smartImageDisplayStrategy = remember { SmartImageDisplayStrategy() }

    val imagePasteItem = getPasteItem(PasteImages::class)

    var index by remember(pasteData.id) { mutableStateOf(0) }

    val filePaths = remember(imagePasteItem) { imagePasteItem.getFilePaths(userDataPathProvider) }
    if (filePaths.isEmpty()) {
        return
    }

    val safeIndex = index.coerceIn(filePaths.indices)
    val imagePath = filePaths[safeIndex]

    val pasteFileCoordinate =
        remember(pasteData.id, imagePath) {
            PasteFileCoordinate(pasteData.getPasteCoordinate(), imagePath)
        }

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val density = LocalDensity.current
    val targetUiSize = appSizeValue.sidePasteContentSize
    val targetSizePx =
        with(density) {
            Size(targetUiSize.width.toPx(), targetUiSize.height.toPx())
        }

    val intSize by produceState<IntSize?>(
        initialValue = null,
        key1 = imagePath,
    ) {
        value = imageHandler.readSize(imagePath)
    }

    val fileSize by produceState(
        initialValue = 0L,
        key1 = imagePath,
    ) {
        value = fileUtils.getFileSize(imagePath)
    }

    val fileFormat = remember(imagePath) { imagePath.extension }

    SidePasteLayoutView(
        pasteBottomContent = {},
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val request =
                remember(pasteFileCoordinate, targetSizePx) {
                    ImageRequest
                        .Builder(platformContext)
                        .data(ImageItem(pasteFileCoordinate, false))
                        .size(width = targetSizePx.width.toInt(), height = targetSizePx.height.toInt())
                        .precision(Precision.INEXACT)
                        .crossfade(true)
                        .build()
                }

            SubcomposeAsyncImage(
                modifier = Modifier.wrapContentSize(),
                model = request,
                imageLoader = imageLoaders.userImageLoader,
                contentDescription = "imageType",
                contentScale = ContentScale.Fit,
                content = {
                    val state by painter.state.collectAsState()
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
                            val intrinsicSize = painter.intrinsicSize

                            val displayResult =
                                smartImageDisplayStrategy.compute(
                                    srcSize = Size(intrinsicSize.width, intrinsicSize.height),
                                    dstSize = targetSizePx,
                                )

                            TransparentBackground(modifier = Modifier.matchParentSize())

                            SubcomposeAsyncImageContent(
                                contentScale = displayResult.contentScale,
                                alignment = displayResult.alignment,
                            )
                        }
                    }
                },
            )

            FlowRow(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = small2X),
                horizontalArrangement = Arrangement.spacedBy(tiny3X),
                verticalArrangement = Arrangement.spacedBy(tiny3X),
            ) {
                ImageFileFormat(format = fileFormat)
                ImageResolution(imageSize = intSize)
                ImageFileSize(fileSize = fileSize)
            }
        }
    }
}
