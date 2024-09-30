package com.crosspaste.ui.search

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.ui.base.PasteTitleView
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SearchListView(setSelectedIndex: (Int) -> Unit) {
    val pasteSearchService = koinInject<PasteSearchService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val searchListState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(scrollState = searchListState)
    var showScrollbar by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }

    val coroutineScope = rememberCoroutineScope()

    val searchResult = remember(pasteSearchService.searchTime) { pasteSearchService.searchResult }

    LaunchedEffect(appWindowManager.showSearchWindow) {
        if (appWindowManager.getShowSearchWindow()) {
            if (pasteSearchService.searchResult.size > 0) {
                pasteSearchService.selectedIndex = 0
                searchListState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(pasteSearchService.selectedIndex, appWindowManager.showSearchWindow) {
        if (appWindowManager.getShowSearchWindow()) {
            val visibleItems = searchListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val lastIndex = visibleItems.last().index

                if (lastIndex < pasteSearchService.selectedIndex) {
                    searchListState.scrollToItem(pasteSearchService.selectedIndex - 9)
                } else if (visibleItems.first().index > pasteSearchService.selectedIndex) {
                    searchListState.scrollToItem(pasteSearchService.selectedIndex)
                }

                if (pasteSearchService.searchResult.size - lastIndex <= 10) {
                    if (pasteSearchService.tryAddLimit()) {
                        pasteSearchService.search(keepSelectIndex = true)
                    }
                }
            }
        }
    }

    LaunchedEffect(searchListState) {
        snapshotFlow {
            searchListState.firstVisibleItemIndex to
                searchListState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (_, _) ->
            val visibleItems = searchListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty() && pasteSearchService.searchResult.size - visibleItems.last().index <= 10) {
                if (pasteSearchService.tryAddLimit()) {
                    pasteSearchService.search(keepSelectIndex = true)
                }
            }

            showScrollbar = pasteSearchService.searchResult.size > 10
            if (showScrollbar) {
                scrollJob?.cancel()
                scrollJob =
                    coroutineScope.launch(CoroutineName("HiddenScroll")) {
                        delay(500)
                        showScrollbar = false
                    }
            }
        }
    }

    LaunchedEffect(pasteSearchService.searchResult.size) {
        if (pasteSearchService.searchResult.size > 10) {
            showScrollbar = true
        }
    }

    Box(modifier = Modifier.width(280.dp).height(420.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                state = searchListState,
                modifier = Modifier.width(280.dp).height(400.dp),
            ) {
                itemsIndexed(
                    searchResult,
                    key = { _, item -> item.id },
                ) { index, pasteData ->
                    PasteTitleView(pasteData, index == pasteSearchService.selectedIndex) {
                        setSelectedIndex(index)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        VerticalScrollbar(
            modifier =
                Modifier.background(color = Color.Transparent)
                    .fillMaxHeight().align(Alignment.CenterEnd)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                coroutineScope.launch(CoroutineName("ScrollPaste")) {
                                    searchListState.scrollBy(-delta)
                                }
                            },
                    ),
            adapter = adapter,
            style =
                ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 300,
                    unhoverColor =
                        if (showScrollbar) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
                        } else {
                            Color.Transparent
                        },
                    hoverColor = MaterialTheme.colorScheme.onBackground,
                ),
        )
    }
}
