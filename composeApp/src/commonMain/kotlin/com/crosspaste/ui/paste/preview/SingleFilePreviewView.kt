package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.FILE_NAME
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.fileSlash
import com.crosspaste.ui.base.folder
import com.crosspaste.utils.getFileUtils
import okio.Path
import org.koin.compose.koinInject

@Composable
fun SingleFilePreviewView(filePath: Path) {
    val copywriter = koinInject<GlobalCopywriter>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val uiSupport = koinInject<UISupport>()

    val fileUtils = getFileUtils()

    val existFile by remember { mutableStateOf(filePath.toFile().exists()) }
    val isFile by remember { mutableStateOf(if (existFile) filePath.toFile().isFile else null) }

    Row(
        modifier =
            Modifier.pointerInput(Unit) {
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
                content = {
                    when (this.painter.state.collectAsState().value) {
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Error,
                        -> {
                            Icon(
                                modifier = Modifier.size(100.dp),
                                painter = isFile?.let { if (it) file() else folder() } ?: fileSlash(),
                                contentDescription = "fileType",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
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
                    .wrapContentWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "${copywriter.getText(FILE_NAME)}: ${filePath.name}",
                color = MaterialTheme.colorScheme.onBackground,
                style =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 10.sp,
                    ),
            )

            if (existFile) {
                val fileSize =
                    remember(filePath) {
                        fileUtils.formatBytes(fileUtils.getFileSize(filePath))
                    }

                Text(
                    text = "${copywriter.getText(SIZE)}: $fileSize",
                    color = MaterialTheme.colorScheme.onBackground,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp,
                        ),
                )
            } else {
                Text(
                    text = copywriter.getText(MISSING_FILE),
                    color = MaterialTheme.colorScheme.error,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 10.sp,
                        ),
                )
            }
        }
    }
}
