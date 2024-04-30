package com.clipevery.ui.clip.detail

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
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ChromeService
import com.clipevery.clip.item.ClipHtml
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.presist.FilePersist
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.loadImageData
import com.clipevery.utils.getDateUtils
import com.clipevery.utils.getFileUtils
import java.awt.Desktop

@Composable
fun HtmlToImageDetailView(
    clipData: ClipData,
    clipHtml: ClipHtml,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val filePersist = current.koin.get<FilePersist>()
    val chromeService = current.koin.get<ChromeService>()

    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()

    val filePath by remember(clipData.id) { mutableStateOf(clipHtml.getHtmlImagePath()) }

    var existFile by remember(clipData.id) { mutableStateOf(filePath.toFile().exists()) }

    ClipDetailView(
        detailView = {
            AsyncView(
                key = clipData.id,
                load = {
                    if (!existFile) {
                        chromeService.html2Image(clipHtml.html)?.let { bytes ->
                            filePersist.createOneFilePersist(filePath).saveBytes(bytes)
                            existFile = true
                        }
                    }
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
                                            if (Desktop.isDesktopSupported()) {
                                                fileUtils.createTempFile(
                                                    clipHtml.html.toByteArray(),
                                                    fileUtils.createRandomFileName("html"),
                                                )?.let {
                                                        path ->
                                                    val desktop = Desktop.getDesktop()
                                                    desktop.browse(path.toFile().toURI())
                                                }
                                            }
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
                                text = clipHtml.html,
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
            ClipDetailInfoView(
                clipData = clipData,
                items =
                    listOf(
                        ClipDetailInfoItem("Type", copywriter.getText("Html")),
                        ClipDetailInfoItem("Size", clipData.size.toString()),
                        ClipDetailInfoItem("Remote", copywriter.getText(if (clipData.remote) "Yes" else "No")),
                        ClipDetailInfoItem(
                            "Date",
                            copywriter.getDate(
                                dateUtils.convertRealmInstantToLocalDateTime(clipData.createTime),
                            ),
                        ),
                    ),
            )
        },
    )
}
