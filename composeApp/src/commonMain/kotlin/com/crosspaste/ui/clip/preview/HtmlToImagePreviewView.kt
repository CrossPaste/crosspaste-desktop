package com.crosspaste.ui.clip.preview

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
import com.crosspaste.clip.item.ClipHtml
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.LoadImageData
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.loadImageData
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HtmlToImagePreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val current = LocalKoinApplication.current
        val density = LocalDensity.current

        val uiSupport = current.koin.get<UISupport>()

        val clipHtml = it as ClipHtml

        val filePath by remember(clipData.id) { mutableStateOf(clipHtml.getHtmlImagePath()) }

        ClipSpecificPreviewContentView(
            clipMainContent = {
                Row {
                    AsyncView(
                        key = clipData.id,
                        load = {
                            while (!filePath.toFile().exists()) {
                                delay(200)
                            }
                            loadImageData(filePath, density)
                        },
                        loadFor = { loadImageView ->
                            when (loadImageView) {
                                is LoadImageData -> {
                                    BoxWithConstraints(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(5.dp))
                                                .onClick {
                                                    uiSupport.openHtml(it.html)
                                                },
                                    ) {
                                        Image(
                                            painter = loadImageView.toPainterImage.toPainter(),
                                            contentDescription = "Html 2 Image",
                                            alignment = Alignment.TopStart,
                                            contentScale = ContentScale.None,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }

                                else -> {
                                    Text(
                                        text = clipHtml.getText(),
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
            clipRightInfo = { toShow ->
                ClipMenuView(
                    clipData = clipData,
                    toShow = toShow,
                )
            },
        )
    }
}
