package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.ImageData
import com.crosspaste.image.ImageDataLoader
import com.crosspaste.image.LoadingStateData
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HtmlToImageDetailView(
    pasteData: PasteData,
    pasteHtml: PasteHtml,
    onDoubleClick: () -> Unit,
) {
    val density = LocalDensity.current
    val copywriter = koinInject<GlobalCopywriter>()
    val imageDataLoader = koinInject<ImageDataLoader>()
    val uiSupport = koinInject<UISupport>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val pasteItem = pasteHtml as PasteItem

    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()

    val filePath by remember(pasteData.id) {
        mutableStateOf(
            pasteHtml.getHtmlImagePath(userDataPathProvider),
        )
    }

    var existFile by remember(pasteData.id) { mutableStateOf(filePath.toFile().exists()) }

    PasteDetailView(
        detailView = {
            AsyncView(
                key = pasteData.id,
                defaultValue =
                    if (existFile) {
                        imageDataLoader.loadImageData(filePath, density)
                    } else {
                        LoadingStateData
                    },
                load = {
                    while (!filePath.toFile().exists()) {
                        delay(200)
                    }
                    existFile = true
                    imageDataLoader.loadImageData(filePath, density)
                },
                loadFor = { loadData ->
                    when (loadData) {
                        is ImageData<*> -> {
                            val horizontalScrollState = rememberScrollState()
                            val verticalScrollState = rememberScrollState()

                            BoxWithConstraints(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(horizontalScrollState)
                                        .verticalScroll(verticalScrollState)
                                        .onClick(
                                            onDoubleClick = onDoubleClick,
                                            onClick = {
                                                uiSupport.openHtml(pasteData.id, pasteHtml.html)
                                            },
                                        ),
                            ) {
                                Image(
                                    painter = loadData.readPainter(),
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
                                        color = MaterialTheme.colorScheme.onBackground,
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
                        PasteDetailInfoItem(TYPE, copywriter.getText("html")),
                        PasteDetailInfoItem(SIZE, fileUtils.formatBytes(pasteItem.size)),
                        PasteDetailInfoItem(REMOTE, copywriter.getText(if (pasteData.remote) "yes" else "no")),
                        PasteDetailInfoItem(
                            DATE,
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
