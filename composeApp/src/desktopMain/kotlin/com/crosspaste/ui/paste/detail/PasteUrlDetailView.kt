package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasteUrlDetailView(
    pasteData: PasteData,
    pasteUrl: PasteUrl,
    onDoubleClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()
    val url = pasteUrl.url
    val pasteItem = pasteUrl as PasteItem

    PasteDetailView(
        detailView = {
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        .onClick(
                            onDoubleClick = onDoubleClick,
                            onClick = {
                                uiSupport.openUrlInBrowser(pasteUrl.url)
                            },
                        )
                        .padding(10.dp),
            ) {
                Text(
                    text = url,
                    modifier = Modifier.fillMaxSize(),
                    overflow = TextOverflow.Ellipsis,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                        ),
                )
            }
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem(TYPE, copywriter.getText("link")),
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
