package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.PlatformContext
import com.crosspaste.app.UserAttentionService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.ImageHandler
import com.crosspaste.image.coil.ImageLoaderQualifiers
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteImages
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.paste.item.isInDownloads
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.ImageFileFormat
import com.crosspaste.ui.base.ImageFileSize
import com.crosspaste.ui.base.ImageInfoLabel
import com.crosspaste.ui.base.ImageResolution
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject
import java.awt.image.BufferedImage

@Composable
fun PasteDataScope.ImageSidePreviewView() {
    val userImageLoader = koinInject<ImageLoader>(qualifier = ImageLoaderQualifiers.USER_IMAGE)
    val imageHandler = koinInject<ImageHandler<BufferedImage>>()
    val platformContext = koinInject<PlatformContext>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val userAttentionService = koinInject<UserAttentionService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val fileUtils = remember { getFileUtils() }
    val smartImageDisplayStrategy = remember { SmartImageDisplayStrategy() }

    val imagePasteItem = getPasteItem(PasteImages::class)
    val imageCount = remember(pasteData.id) { imagePasteItem.getDirectChildrenCount() }
    val isInDownloads = remember(imagePasteItem) { imagePasteItem.isInDownloads() }

    var index by remember(pasteData.id) { mutableStateOf(0) }

    val filePaths = remember(imagePasteItem) { imagePasteItem.getFilePaths(userDataPathProvider) }
    if (filePaths.isEmpty()) return

    val safeIndex = index.coerceIn(filePaths.indices)
    val imagePath = filePaths[safeIndex]

    val pasteFileCoordinate =
        remember(pasteData.id, imagePath) {
            PasteFileCoordinate(pasteData.getPasteCoordinate(), imagePath)
        }

    val density = LocalDensity.current
    val targetUiSize = LocalDesktopAppSizeValueState.current.sidePasteContentSize
    val targetSizePx =
        with(density) {
            Size(targetUiSize.width.toPx(), targetUiSize.height.toPx())
        }

    val intSize by produceState<IntSize?>(initialValue = null, key1 = imagePath) {
        value = imageHandler.readSize(imagePath)
    }

    val fileSize by produceState(initialValue = 0L, key1 = imagePath) {
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
            if (imagePath.isAnimatedImage()) {
                AnimatedImageSidePreview(
                    imagePath = imagePath,
                    targetSizePx = targetSizePx,
                    smartImageDisplayStrategy = smartImageDisplayStrategy,
                    userAttentionService = userAttentionService,
                )
            } else {
                StaticImageSidePreview(
                    imagePath = imagePath,
                    pasteFileCoordinate = pasteFileCoordinate,
                    intSize = intSize,
                    targetSizePx = targetSizePx,
                    smartImageDisplayStrategy = smartImageDisplayStrategy,
                    imageLoader = userImageLoader,
                    platformContext = platformContext,
                )
            }

            ImageCountBadge(imageCount)

            ImageInfoLabels(
                isInDownloads = isInDownloads,
                fileFormat = fileFormat,
                intSize = intSize,
                fileSize = fileSize,
                copywriter = copywriter,
            )
        }
    }
}

@Composable
private fun BoxScope.ImageCountBadge(imageCount: Long) {
    if (imageCount <= 1L) return
    val label = remember(imageCount) { if (imageCount > 99) "99+" else imageCount.toString() }
    Badge(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = medium, end = medium),
    ) {
        Text(label)
    }
}

@Composable
private fun BoxScope.ImageInfoLabels(
    isInDownloads: Boolean,
    fileFormat: String,
    intSize: IntSize?,
    fileSize: Long,
    copywriter: GlobalCopywriter,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = small2X),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tiny3X),
    ) {
        if (isInDownloads) {
            ImageInfoLabel(text = copywriter.getText("in_downloads"))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(tiny3X, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(tiny3X),
        ) {
            ImageFileFormat(format = fileFormat)
            ImageResolution(imageSize = intSize)
            ImageFileSize(fileSize = fileSize)
        }
    }
}
