package com.clipevery.ui.clip.detail

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
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipUrl
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.clip.preview.openUrlInBrowser
import com.clipevery.utils.getDateUtils

@Composable
fun ClipUrlDetailView(
    clipData: ClipData,
    clipUrl: ClipUrl,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val dateUtils = getDateUtils()
    val url = clipUrl.url
    val clipAppearItem = clipUrl as ClipAppearItem

    ClipDetailView(
        detailView = {
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        .clickable {
                            openUrlInBrowser(clipUrl.url)
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
            ClipDetailInfoView(
                clipData = clipData,
                items =
                    listOf(
                        ClipDetailInfoItem("Type", copywriter.getText("Link")),
                        ClipDetailInfoItem("Size", clipAppearItem.size.toString()),
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
