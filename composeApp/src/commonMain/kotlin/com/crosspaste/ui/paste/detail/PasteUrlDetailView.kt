package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils

@Composable
fun PasteUrlDetailView(
    pasteData: PasteData,
    pasteUrl: PasteUrl,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val uiSupport = current.koin.get<UISupport>()
    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()
    val url = pasteUrl.url
    val pasteItem = pasteUrl as PasteItem

    PasteDetailView(
        detailView = {
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        .clickable {
                            uiSupport.openUrlInBrowser(pasteUrl.url)
                        }.padding(10.dp),
            ) {
                Text(
                    text = url,
                    modifier = Modifier.fillMaxSize(),
                    overflow = TextOverflow.Ellipsis,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colors.primary,
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
                        PasteDetailInfoItem("Type", copywriter.getText("Link")),
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
