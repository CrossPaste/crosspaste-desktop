package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.crosspaste.paste.item.PasteText
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils

@Composable
fun PasteTextDetailView(
    pasteData: PasteData,
    pasteText: PasteText,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()
    val text = pasteText.text
    val pasteItem = pasteText as PasteItem

    PasteDetailView(
        detailView = {
            Row(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    text = text,
                    modifier =
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    overflow = TextOverflow.Ellipsis,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colors.onBackground,
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
                        PasteDetailInfoItem("Type", copywriter.getText("Text")),
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
