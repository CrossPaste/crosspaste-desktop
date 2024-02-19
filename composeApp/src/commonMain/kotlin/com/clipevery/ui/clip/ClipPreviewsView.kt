package com.clipevery.ui.clip

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClipPreviewsView() {
    val current = LocalKoinApplication.current
    val clipDao = current.koin.get<ClipDao>()

    val clipDataList: List<ClipData> = clipDao.getClipData(limit = 20)

    val listState = rememberLazyListState()
    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val rememberClipDataList = remember { mutableStateListOf<ClipData>().apply {
        addAll(clipDataList)
    } }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && visibleItems.last().index == rememberClipDataList.size - 1) {
                    // todo loadMoreItems()
                }
                isScrolling = true
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    delay(500)
                    isScrolling = false
                }
            }
    }

    Box(modifier = Modifier.fillMaxWidth()) {


        LazyColumn(
            state = listState,
            modifier = Modifier.wrapContentHeight()
        ) {
            items(rememberClipDataList) { clipData ->
                ClipPreviewItemView(clipData)
            }
        }

        VerticalScrollbar(
            modifier = Modifier.background(color = Color.Transparent)
                .fillMaxHeight().align(Alignment.CenterEnd)
                .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        listState.scrollBy(-delta)
                    }
                },
            ),
            adapter = rememberScrollbarAdapter(scrollState = listState),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = if (isScrolling) MaterialTheme.colors.onBackground.copy(alpha = 0.48f) else Color.Transparent,
                hoverColor = MaterialTheme.colors.onBackground
            )
        )
    }
}
