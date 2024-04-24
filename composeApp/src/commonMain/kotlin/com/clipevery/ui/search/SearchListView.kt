package com.clipevery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.dao.clip.ClipData
import com.clipevery.ui.clip.ClipTypeIconView
import com.clipevery.ui.clip.title.getClipTitle

@Composable
fun SearchListView(setSelectedIndex: (Int) -> Unit) {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.width(280.dp).height(520.dp),
    ) {
        itemsIndexed(
            clipSearchService.searchResult,
            key = { _, item -> item.id },
        ) { index, clipData ->
            ClipTitleView(clipData, index == clipSearchService.selectedIndex.value) {
                setSelectedIndex(index)
            }
        }
    }
}

@Composable
fun ClipTitleView(
    clipData: ClipData,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title by remember(clipData.clipState) { mutableStateOf(getClipTitle(clipData)) }

    title?.let {
        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colors.surface else MaterialTheme.colors.background)
                        .clickable { onClick() },
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = ClipTypeIconView(clipData.clipType),
                        contentDescription = "Clip Type Icon",
                        modifier = Modifier.padding(3.dp).size(14.dp),
                        tint = MaterialTheme.colors.onBackground,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.onBackground,
                        style =
                            TextStyle(
                                textAlign = TextAlign.Start,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                    )
                }
            }
        }
    }
}
