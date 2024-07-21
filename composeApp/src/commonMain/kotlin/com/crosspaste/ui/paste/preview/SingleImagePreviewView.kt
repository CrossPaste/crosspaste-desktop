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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.ImageData
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.image.getImageDataLoader
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.utils.getFileUtils
import okio.FileSystem
import okio.Path

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleImagePreviewView(imagePath: Path) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val uiSupport = current.koin.get<UISupport>()
    val thumbnailLoader = current.koin.get<ThumbnailLoader>()

    val fileUtils = getFileUtils()
    val imageDataLoader = getImageDataLoader()

    val existFile by remember { mutableStateOf(FileSystem.SYSTEM.exists(imagePath)) }

    Row(
        modifier =
            Modifier.onClick {
                uiSupport.openImage(imagePath)
            },
    ) {
        AsyncView(
            key = imagePath,
            load = {
                imageDataLoader.loadImageData(imagePath, density, thumbnailLoader)
            },
            loadFor = { loadData ->
                Box {
                    TransparentBackground(
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(5.dp)),
                    )

                    if (loadData.isSuccess() && loadData is ImageData<*>) {
                        ShowImageView(
                            painter = loadData.readPainter(),
                            contentDescription = imagePath.name,
                        )
                    } else if (loadData.isLoading()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(100.dp),
                        )
                    } else {
                        ShowImageView(
                            painter = imageSlash(),
                            contentDescription = imagePath.name,
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
                        text = "${copywriter.getText("file_name")}: ${imagePath.name}",
                        color = MaterialTheme.colors.onBackground,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                fontSize = 10.sp,
                            ),
                    )

                    if (loadData.isSuccess() && loadData is ImageData<*>) {
                        val floatSize = loadData.readPainter().intrinsicSize
                        val size = IntSize(floatSize.width.toInt(), floatSize.height.toInt())
                        Text(
                            text =
                                "${copywriter.getText("dimensions")}: " +
                                    "${size.width} x ${size.height}",
                            color = MaterialTheme.colors.onBackground,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 10.sp,
                                ),
                        )
                    }

                    if (existFile) {
                        val imageSize =
                            remember(imagePath) {
                                fileUtils.formatBytes(fileUtils.getFileSize(imagePath))
                            }

                        Text(
                            text = "${copywriter.getText("size")}: $imageSize",
                            color = MaterialTheme.colors.onBackground,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 10.sp,
                                ),
                        )
                    } else {
                        Text(
                            text = copywriter.getText("missing_file"),
                            color = MaterialTheme.colors.error,
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
