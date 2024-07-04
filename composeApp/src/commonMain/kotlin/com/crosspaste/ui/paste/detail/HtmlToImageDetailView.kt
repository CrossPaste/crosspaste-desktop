package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.LoadImageData
import com.crosspaste.ui.base.LoadingStateData
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.loadImageData
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.delay

@Composable
fun HtmlToImageDetailView(
    pasteData: PasteData,
    pasteHtml: PasteHtml,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val uiSupport = current.koin.get<UISupport>()
    val pasteItem = pasteHtml as PasteItem

    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()

    val filePath by remember(pasteData.id) { mutableStateOf(pasteHtml.getHtmlImagePath()) }

    var existFile by remember(pasteData.id) { mutableStateOf(filePath.toFile().exists()) }

    PasteDetailView(
        detailView = {
            AsyncView(
                key = pasteData.id,
                defaultValue = if (existFile) loadImageData(filePath, density) else LoadingStateData,
                load = {
                    while (!filePath.toFile().exists()) {
                        delay(200)
                    }
                    existFile = true
                    loadImageData(filePath, density)
                },
                loadFor = { loadImageView ->
                    when (loadImageView) {
                        is LoadImageData -> {
                            val horizontalScrollState = rememberScrollState()
                            val verticalScrollState = rememberScrollState()

                            BoxWithConstraints(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(horizontalScrollState)
                                        .verticalScroll(verticalScrollState)
                                        .clickable {
                                            uiSupport.openHtml(pasteHtml.html)
                                        },
                            ) {
                                Image(
                                    painter = loadImageView.toPainterImage.toPainter(),
                                    contentDescription = "Html 2 Image",
                                    modifier = Modifier.wrapContentSize(),
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
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem("Type", copywriter.getText("Html")),
                        PasteDetailInfoItem("Size", fileUtils.formatBytes(pasteItem.size)),
                        PasteDetailInfoItem("Remote", copywriter.getText(if (pasteData.remote) "Yes" else "No")),
                        PasteDetailInfoItem(
                            "Date",
                            copywriter.getDate(
                                dateUtils.convertRealmInstantToLocalDateTime(pasteData.createTime),
                                true,
                            ),
                        ),
                    ),
            )
        },
    )
}
