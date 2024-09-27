package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.onClick
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.image.coil.Html2ImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HtmlToImagePreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val imageLoaders = koinInject<ImageLoaders>()

        val pasteHtml = it as PasteHtml

        val filePath by remember(pasteData.id) {
            mutableStateOf(
                pasteHtml.getHtmlImagePath(userDataPathProvider),
            )
        }

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row {
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(5.dp))
                                .onClick(
                                    onDoubleClick = onDoubleClick,
                                    onClick = {},
                                ),
                    ) {
                        val density = LocalDensity.current
                        SubcomposeAsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model =
                                ImageRequest.Builder(PlatformContext.INSTANCE)
                                    .data(Html2ImageItem(filePath, density))
                                    .crossfade(true)
                                    .build(),
                            imageLoader = imageLoaders.html2ImageLoader,
                            alignment = Alignment.TopStart,
                            contentScale = ContentScale.None,
                            contentDescription = "Html 2 Image",
                            content = {
                                when (this.painter.state.collectAsState().value) {
                                    is AsyncImagePainter.State.Loading,
                                    is AsyncImagePainter.State.Error,
                                    -> {
                                        Text(
                                            text = pasteHtml.getText(),
                                            fontFamily = FontFamily.SansSerif,
                                            maxLines = 4,
                                            softWrap = true,
                                            overflow = TextOverflow.Ellipsis,
                                            style =
                                                TextStyle(
                                                    fontWeight = FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 14.sp,
                                                ),
                                        )
                                    }

                                    else -> {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            },
                        )
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(
                    pasteData = pasteData,
                    toShow = toShow,
                )
            },
        )
    }
}
