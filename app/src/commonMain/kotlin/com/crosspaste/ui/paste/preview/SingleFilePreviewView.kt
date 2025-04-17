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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.paste.item.PasteFileInfoTreeCoordinate
import com.crosspaste.ui.base.FileIcon
import com.crosspaste.ui.base.FileSlashIcon
import com.crosspaste.ui.base.FolderIcon
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun SingleFilePreviewView(
    pasteFileInfoTreeCoordinate: PasteFileInfoTreeCoordinate,
    width: Dp,
) {
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
            Modifier.width(width)
                .wrapContentHeight()
                .clip(RoundedCornerShape(5.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            uiSupport.browseFile(filePath)
                        },
                    )
                },
    ) {
        Box(modifier = Modifier.size(100.dp)) {
            SubcomposeAsyncImage(
                modifier = Modifier.fillMaxSize(),
                model =
                    ImageRequest.Builder(platformContext)
                        .data(FileExtItem(filePath))
                        .crossfade(true)
                        .build(),
                imageLoader = imageLoaders.fileExtImageLoader,
                contentDescription = "fileType",
                alignment = Alignment.Center,
                content = {
                    when (this.painter.state.collectAsState().value) {
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Error,
                        -> {
                            val modifier = Modifier.padding(10.dp).size(90.dp)
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
                Modifier.fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = filePath.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(modifier = Modifier.height(4.dp))

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
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                Text(
                    text = copywriter.getText(MISSING_FILE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
