package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.ImageInfoBuilder
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.image.coil.ImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.DIMENSIONS
import com.crosspaste.info.PasteInfos.FILE_NAME
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun SingleImagePreviewView(
    pasteFileCoordinate: PasteFileCoordinate,
    width: Dp,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val thumbnailLoader = koinInject<ThumbnailLoader>()
    val uiSupport = koinInject<UISupport>()

    val fileUtils = getFileUtils()

    val existFile by remember {
        mutableStateOf(
            fileUtils.existFile(pasteFileCoordinate.filePath),
        )
    }

    Row(
        modifier =
            Modifier.width(width)
                .wrapContentHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            uiSupport.openImage(pasteFileCoordinate.filePath)
                        },
                    )
                },
    ) {
        SubcomposeAsyncImage(
            modifier = Modifier.fillMaxSize(),
            model =
                ImageRequest.Builder(platformContext)
                    .data(ImageItem(pasteFileCoordinate, true))
                    .crossfade(true)
                    .build(),
            imageLoader = imageLoaders.userImageLoader,
            contentDescription = "imageType",
            content = {
                val context = this
                val state = context.painter.state.collectAsState().value
                Row {
                    Box(
                        modifier =
                            Modifier.size(100.dp)
                                .clip(RoundedCornerShape(5.dp)),
                    ) {
                        TransparentBackground(
                            modifier = Modifier.size(100.dp),
                        )
                        when (state) {
                            is AsyncImagePainter.State.Loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(100.dp),
                                )
                            }

                            is AsyncImagePainter.State.Error,
                            -> {
                                Icon(
                                    modifier = Modifier.size(100.dp),
                                    painter = imageSlash(),
                                    contentDescription = pasteFileCoordinate.filePath.name,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }

                            else -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    context.SubcomposeAsyncImageContent(
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier =
                            Modifier.fillMaxHeight()
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = "${copywriter.getText(FILE_NAME)}: ${pasteFileCoordinate.filePath.name}",
                            maxLines = 3,
                            overflow = TextOverflow.Visible,
                            color = MaterialTheme.colorScheme.onBackground,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 10.sp,
                                ),
                        )

                        if (state is AsyncImagePainter.State.Success) {
                            val builder = ImageInfoBuilder()
                            thumbnailLoader.readOriginMeta(pasteFileCoordinate, builder)
                            val imageInfo = builder.build()
                            imageInfo.map[DIMENSIONS]?.let {
                                Text(
                                    text = "${copywriter.getText(DIMENSIONS)}: ${it.getTextByCopyWriter(copywriter)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
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
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
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
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
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
                }
            },
        )
    }
}
