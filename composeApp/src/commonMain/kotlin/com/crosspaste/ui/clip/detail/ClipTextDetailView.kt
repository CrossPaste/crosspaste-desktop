package com.crosspaste.ui.clip.detail

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
import com.crosspaste.clip.item.ClipText
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils

@Composable
fun ClipTextDetailView(
    clipData: ClipData,
    clipText: ClipText,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()
    val text = clipText.text
    val clipItem = clipText as ClipItem

    ClipDetailView(
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
            ClipDetailInfoView(
                clipData = clipData,
                items =
                    listOf(
                        ClipDetailInfoItem("Type", copywriter.getText("Text")),
                        ClipDetailInfoItem("Size", fileUtils.formatBytes(clipItem.size)),
                        ClipDetailInfoItem("Remote", copywriter.getText(if (clipData.remote) "Yes" else "No")),
                        ClipDetailInfoItem(
                            "Date",
                            copywriter.getDate(
                                dateUtils.convertRealmInstantToLocalDateTime(clipData.createTime),
                                true,
                            ),
                        ),
                    ),
            )
        },
    )
}
