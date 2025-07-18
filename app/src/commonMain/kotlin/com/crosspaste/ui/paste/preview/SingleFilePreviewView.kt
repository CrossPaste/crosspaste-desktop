package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.app.AppSize
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.paste.item.PasteFileInfoTreeCoordinate
import com.crosspaste.ui.base.FileIcon
import com.crosspaste.ui.base.FileSlashIcon
import com.crosspaste.ui.base.FolderIcon
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUIFont.propertyTextStyle
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun SingleFilePreviewView(
    pasteFileInfoTreeCoordinate: PasteFileInfoTreeCoordinate,
    width: Dp,
) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val uiSupport = koinInject<UISupport>()

    val fileUtils = getFileUtils()

    val filePath = pasteFileInfoTreeCoordinate.filePath

    val existFile by remember(filePath) {
        mutableStateOf(fileUtils.existFile(filePath))
    }
    val isFile by remember(filePath) {
        mutableStateOf(pasteFileInfoTreeCoordinate.fileInfoTree.isFile())
    }

    Row(
        modifier =
            Modifier
                .width(width)
                .wrapContentHeight()
                .clip(tiny2XRoundedCornerShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            uiSupport.browseFile(filePath)
                        },
                    )
                },
    ) {
        Box(modifier = Modifier.size(appSize.mainPasteSize.height)) {
            SubcomposeAsyncImage(
                modifier = Modifier.fillMaxSize(),
                model =
                    ImageRequest
                        .Builder(platformContext)
                        .data(FileExtItem(filePath))
                        .crossfade(true)
                        .build(),
                imageLoader = imageLoaders.fileExtImageLoader,
                contentDescription = "fileType",
                alignment = Alignment.Center,
                content = {
                    when (
                        this.painter.state
                            .collectAsState()
                            .value
                    ) {
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Error,
                        -> {
                            val modifier =
                                Modifier
                                    .padding(small3X)
                                    .size(appSize.mainPasteSize.height - small3X)
                            if (existFile) {
                                if (isFile) {
                                    FileIcon(modifier)
                                } else {
                                    FolderIcon(modifier)
                                }
                            } else {
                                FileSlashIcon(modifier)
                            }
                        }

                        else -> {
                            SubcomposeAsyncImageContent()
                        }
                    }
                },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = tiny)
                    .padding(bottom = tiny),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = filePath.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                style = propertyTextStyle,
            )

            Spacer(modifier = Modifier.height(tiny3X))

            if (existFile) {
                val fileSize =
                    remember(filePath) {
                        fileUtils.formatBytes(fileUtils.getFileSize(filePath))
                    }
                Text(
                    text = fileSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = propertyTextStyle,
                )
            } else {
                Text(
                    text = copywriter.getText(MISSING_FILE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.error,
                    style = propertyTextStyle,
                )
            }
        }
    }
}
