package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageData
import com.crosspaste.image.ImageDataLoader
import com.crosspaste.info.PasteInfos.FILE_NAME
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.fileSlash
import com.crosspaste.utils.getFileUtils
import okio.Path
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleFilePreviewView(filePath: Path) {
    val density = LocalDensity.current
    val copywriter = koinInject<GlobalCopywriter>()
    val imageDataLoader = koinInject<ImageDataLoader>()
    val uiSupport = koinInject<UISupport>()
    val fileExtIconLoader = koinInject<FileExtImageLoader>()

    val fileUtils = getFileUtils()

    val existFile by remember { mutableStateOf(filePath.toFile().exists()) }
    val isFile by remember { mutableStateOf(if (existFile) filePath.toFile().isFile else null) }

    Row(
        modifier =
            Modifier.onClick {
                uiSupport.browseFile(filePath)
            },
    ) {
        Box(modifier = Modifier.size(100.dp)) {
            AsyncView(
                key = filePath,
                load = {
                    fileExtIconLoader.load(filePath)?.let {
                        imageDataLoader.loadImageData(it, density)
                    } ?: imageDataLoader.loadIconData(isFile, density)
                },
                loadFor = { loadData ->
                    if (loadData.isSuccess() && loadData is ImageData<*>) {
                        if (!loadData.isIcon) {
                            Image(
                                modifier =
                                    Modifier.size(100.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                painter = loadData.readPainter(),
                                contentDescription = "fileType",
                            )
                        } else {
                            Icon(
                                modifier = Modifier.size(100.dp),
                                painter = loadData.readPainter(),
                                contentDescription = "fileType",
                                tint = MaterialTheme.colors.onBackground,
                            )
                        }
                    } else if (loadData.isError()) {
                        Icon(
                            modifier = Modifier.size(100.dp),
                            painter = fileSlash(),
                            contentDescription = "fileType",
                            tint = MaterialTheme.colors.onBackground,
                        )
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
                color = MaterialTheme.colors.onBackground,
                style =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
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
                    color = MaterialTheme.colors.onBackground,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp,
                        ),
                )
            } else {
                Text(
                    text = copywriter.getText(MISSING_FILE),
                    color = MaterialTheme.colors.error,
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
