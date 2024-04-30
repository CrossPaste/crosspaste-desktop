package com.clipevery.ui.clip.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.starRegular
import com.clipevery.ui.base.starSolid

data class ClipDetailInfoItem(val key: String, val value: String)

@Composable
fun ClipDetailInfoView(
    clipData: ClipData,
    items: List<ClipDetailInfoItem>,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val clipDao = current.koin.get<ClipDao>()

    val listState = rememberLazyListState()

    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copywriter.getText("Information"),
            style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 16.sp,
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            modifier =
                Modifier.size(15.dp).clickable {
                    clipDao.setFavorite(clipData.id, !clipData.favorite)
                },
            painter = if (clipData.favorite) starSolid() else starRegular(),
            contentDescription = "Favorite",
            tint = if (clipData.favorite) Color(0xFFFFCE34) else MaterialTheme.colors.onSurface,
        )
    }
    Spacer(modifier = Modifier.height(10.dp))

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.key },
        ) { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copywriter.getText(item.key),
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 15.sp,
                        ),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = item.value,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 15.sp,
                        ),
                )
            }
            if (index != items.size - 1) {
                Divider(
                    modifier = Modifier.fillMaxWidth().height(1.dp),
                    thickness = 2.dp,
                )
            }
        }
    }
}
