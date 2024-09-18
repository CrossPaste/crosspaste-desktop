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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.ImageData
import com.crosspaste.image.ImageDataLoader
import com.crosspaste.info.PasteInfos.DIMENSIONS
import com.crosspaste.info.PasteInfos.FILE_NAME
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.utils.getFileUtils
import okio.FileSystem
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleImagePreviewView(pasteFileCoordinate: PasteFileCoordinate) {
    val density = LocalDensity.current
    val copywriter = koinInject<GlobalCopywriter>()
    val imageDataLoader = koinInject<ImageDataLoader>()
    val uiSupport = koinInject<UISupport>()

    val fileUtils = getFileUtils()

    val existFile by remember {
        mutableStateOf(
            FileSystem.SYSTEM.exists(pasteFileCoordinate.filePath),
        )
    }

    Row(
        modifier =
            Modifier.onClick {
                uiSupport.openImage(pasteFileCoordinate.filePath)
            },
    ) {
        AsyncView(
            key = pasteFileCoordinate.filePath,
            load = {
                imageDataLoader.loadImageData(pasteFileCoordinate, density, true)
            },
            loadFor = { loadData ->
                Box {
                    TransparentBackground(
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(5.dp)),
                    )

                    if (loadData.isSuccess() && loadData is ImageData<*>) {
                        ShowImageView(
                            painter = loadData.readPainter(),
                            contentDescription = pasteFileCoordinate.filePath.name,
                        )
                    } else if (loadData.isLoading()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(100.dp),
                        )
                    } else {
                        ShowImageView(
                            painter = imageSlash(),
                            contentDescription = pasteFileCoordinate.filePath.name,
                        )
                    }
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
                        text = "${copywriter.getText(FILE_NAME)}: ${pasteFileCoordinate.filePath.name}",
                        color = MaterialTheme.colorScheme.onBackground,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                fontSize = 10.sp,
                            ),
                    )

                    if (loadData.isSuccess() && loadData is ImageData<*>) {
                        val imageInfo = loadData.readImageInfo()
                        imageInfo.map[DIMENSIONS]?.let {
                            Text(
                                text = "${copywriter.getText(DIMENSIONS)}: ${it.getTextByCopyWriter(copywriter)}",
                                color = MaterialTheme.colorScheme.onBackground,
                                style =
                                    TextStyle(
                                        fontWeight = FontWeight.Light,
                                        fontSize = 10.sp,
                                    ),
                            )
                        }
                    }

                    if (existFile) {
                        val imageSize =
                            remember(pasteFileCoordinate.filePath) {
                                fileUtils.formatBytes(fileUtils.getFileSize(pasteFileCoordinate.filePath))
                            }

                        Text(
                            text = "${copywriter.getText(SIZE)}: $imageSize",
                            color = MaterialTheme.colorScheme.onBackground,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
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
                                    color = Color.Red,
                                    fontSize = 10.sp,
                                ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun ShowImageView(
    painter: Painter,
    contentDescription: String,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.size(100.dp)
                .clip(RoundedCornerShape(5.dp)),
    )
}
