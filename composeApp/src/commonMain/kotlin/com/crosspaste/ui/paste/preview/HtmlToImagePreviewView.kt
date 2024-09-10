package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import com.crosspaste.LocalKoinApplication
import com.crosspaste.image.ImageData
import com.crosspaste.image.getImageDataLoader
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.ui.base.AsyncView
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HtmlToImagePreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val current = LocalKoinApplication.current
        val density = LocalDensity.current
        val userDataPathProvider = current.koin.get<UserDataPathProvider>()
        val imageDataLoader = getImageDataLoader()

        val pasteHtml = it as PasteHtml

        val filePath by remember(pasteData.id) {
            mutableStateOf(
                pasteHtml.getHtmlImagePath(userDataPathProvider),
            )
        }

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row {
                    AsyncView(
                        key = pasteData.id,
                        load = {
                            while (!filePath.toFile().exists()) {
                                delay(200)
                            }
                            imageDataLoader.loadImageData(filePath, density)
                        },
                        loadFor = { loadData ->
                            when (loadData) {
                                is ImageData<*> -> {
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
                                        Image(
                                            painter = loadData.readPainter(),
                                            contentDescription = "Html 2 Image",
                                            alignment = Alignment.TopStart,
                                            contentScale = ContentScale.None,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }

                                else -> {
                                    Text(
                                        text = pasteHtml.getText(),
                                        fontFamily = FontFamily.SansSerif,
                                        maxLines = 4,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        style =
                                            TextStyle(
                                                fontWeight = FontWeight.Normal,
                                                color = MaterialTheme.colors.onBackground,
                                                fontSize = 14.sp,
                                            ),
                                    )
                                }
                            }
                        },
                    )
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
