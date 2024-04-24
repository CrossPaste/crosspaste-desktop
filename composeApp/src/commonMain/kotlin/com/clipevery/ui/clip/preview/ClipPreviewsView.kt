package com.clipevery.ui.clip.preview

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
import androidx.compose.runtime.mutableStateMapOf
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
import com.clipevery.dao.clip.ClipDataSortObject
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId

val clipDataComparator = compareByDescending<ClipData> { it.getClipDataSortObject() }

@Composable
fun ClipPreviewsView() {
    val current = LocalKoinApplication.current
    val clipDao = current.koin.get<ClipDao>()

    val listState = rememberLazyListState()
    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val rememberClipDataList = remember { mutableStateListOf<ClipData>() }
    val rememberObjetIdMap = remember { mutableStateMapOf<ObjectId, ClipData>() }

    LaunchedEffect(Unit) {
        val clipDataList: RealmResults<ClipData> = clipDao.getClipData(limit = 20)

        val clipDatasFlow = clipDataList.asFlow()
        clipDatasFlow.collect { changes: ResultsChange<ClipData> ->
            when (changes) {
                is UpdatedResults -> {
                    if (changes.insertions.isNotEmpty()) {
                        for (i in changes.insertions.size - 1 downTo 0) {
                            val newClipData = changes.list[changes.insertions[i]]
                            var insertionIndex = rememberClipDataList.binarySearch(newClipData, clipDataComparator)
                            if (insertionIndex < 0) {
                                insertionIndex = -(insertionIndex + 1)
                            }
                            if (!rememberObjetIdMap.keys.contains(newClipData.id)) {
                                rememberClipDataList.add(insertionIndex, newClipData)
                                rememberObjetIdMap[newClipData.id] = newClipData
                            }
                        }
                    }

                    val md5Set: MutableSet<String> = mutableSetOf()
                    val clipDataHashSet: MutableSet<ClipDataSortObject> = mutableSetOf()
                    if (changes.changes.isNotEmpty()) {
                        for (i in 0 until changes.changes.size) {
                            val changeItem = changes.list[changes.changes[i]]
                            val changeIndex = rememberClipDataList.binarySearch(changeItem, clipDataComparator)
                            if (changeIndex >= 0) {
                                rememberClipDataList[changeIndex] = changeItem
                                md5Set.add(changeItem.md5)
                                clipDataHashSet.add(changeItem.getClipDataSortObject())
                            }
                        }
                    }

                    if (md5Set.isNotEmpty()) {
                        val iterator = rememberClipDataList.iterator()
                        while (iterator.hasNext()) {
                            val clipData = iterator.next()
                            val hashObject = clipData.getClipDataSortObject()
                            if (md5Set.contains(clipData.md5) && !clipDataHashSet.contains(hashObject)) {
                                iterator.remove()
                            }
                        }
                    }

                    if (listState.firstVisibleItemIndex == 0) {
                        listState.scrollToItem(0)
                    }
                    // changes.deletions handle separately
                }
                is InitialResults -> {
                    val initList = changes.list.sortedWith(clipDataComparator)
                    rememberClipDataList.addAll(initList)
                    rememberObjetIdMap.putAll(initList.map { it.id to it })
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
                                if (!rememberObjetIdMap.keys.contains(clipData.id)) {
                                    rememberClipDataList.add(clipData)
                                    rememberObjetIdMap[clipData.id] = clipData
                                }
                            }
                        }
                    }
                }
                isScrolling = true
                scrollJob?.cancel()
                scrollJob =
                    coroutineScope.launch(CoroutineName("HiddenScroll")) {
                        delay(500)
                        isScrolling = false
                    }
            }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.wrapContentHeight(),
        ) {
            itemsIndexed(
                rememberClipDataList,
                key = { _, item -> item.id },
            ) { index, clipData ->
                ClipPreviewItemView(clipData) {
                    ClipSpecificPreviewView(this)
                }
                if (index != rememberClipDataList.size - 1) {
                    Divider(
                        thickness = 2.dp,
                    )
                }
            }
        }

        VerticalScrollbar(
            modifier =
                Modifier.background(color = Color.Transparent)
                    .fillMaxHeight().align(Alignment.CenterEnd)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                coroutineScope.launch(CoroutineName("ScrollClip")) {
                                    listState.scrollBy(-delta)
                                }
                            },
                    ),
            adapter = rememberScrollbarAdapter(scrollState = listState),
            style =
                ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 300,
                    unhoverColor = if (isScrolling) MaterialTheme.colors.onBackground.copy(alpha = 0.48f) else Color.Transparent,
                    hoverColor = MaterialTheme.colors.onBackground,
                ),
        )
    }
}
