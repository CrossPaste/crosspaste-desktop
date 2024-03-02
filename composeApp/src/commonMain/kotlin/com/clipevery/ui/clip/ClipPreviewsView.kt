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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val clipDataComparator = compareByDescending<ClipData> { it.createTime }
    .thenBy{ it.appInstanceId }
    .thenBy { it.clipId }

@Composable
fun ClipPreviewsView() {
    val current = LocalKoinApplication.current
    val clipDao = current.koin.get<ClipDao>()

    val clipDataList: RealmResults<ClipData> = clipDao.getClipData(limit = 20)

    val listState = rememberLazyListState()
    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val rememberClipDataList = remember { mutableStateListOf<ClipData>().apply {
        addAll(clipDataList.sortedWith(clipDataComparator))
    } }

    LaunchedEffect(Unit) {
        val clipDatasFlow = clipDataList.asFlow()
        clipDatasFlow.collect { changes: ResultsChange<ClipData> ->
            when (changes) {
                is UpdatedResults -> {
                    changes.insertions
                    changes.insertionRanges
                    val firstClipData: ClipData? = rememberClipDataList.firstOrNull()

                    firstClipData?.let {
                        val newClipDataList = clipDao.getClipDataGreaterThan(createTime = it.createTime)
                        for (clipData in newClipDataList.reversed()) {
                            if (clipDataComparator.compare(clipData, firstClipData) < 0) {
                                rememberClipDataList.add(0, clipData)
                            }
                        }
                    } ?: run {
                        val newClipDataList = clipDao.getClipData(limit = 20)
                        rememberClipDataList.addAll(newClipDataList.sortedWith(clipDataComparator))
                    }

                    changes.changes
                    changes.changeRanges

                    rememberClipDataList.forEachIndexed { index, currentElement ->
                        if (currentElement.clipType == ClipType.INVALID) {
                            clipDao.getClipData(currentElement.id)?.let {
                                rememberClipDataList[index] = it
                            }
                        }
                    }

                    changes.deletions
                    changes.deletionRanges

                    val iterator = rememberClipDataList.iterator()
                    while (iterator.hasNext()) {
                        val clipData = iterator.next()
                        if (clipDao.getClipData(clipData.id) == null) {
                            iterator.remove()
                        }
                    }
                }
                else -> {
                    // types other than UpdatedResults are not changes -- ignore them
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && visibleItems.last().index == rememberClipDataList.size - 1) {
                    val lastClipData: ClipData? = rememberClipDataList.lastOrNull()
                    lastClipData?.let {
                        val newClipDataList = clipDao.getClipDataLessThan(createTime = it.createTime, limit = 20)
                        for (clipData in newClipDataList) {
                            if (clipDataComparator.compare(clipData, lastClipData) > 0) {
                                rememberClipDataList.add(clipData)
                            }
                        }
                    }
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
            itemsIndexed(rememberClipDataList) { index, clipData ->
                ClipPreviewItemView(clipData) {
                    ClipSpecificPreviewView(this)
                }
                if (index != rememberClipDataList.size - 1) {
                    Divider(
                        color = MaterialTheme.colors.onBackground,
                        thickness = 2.dp
                    )
                }
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
