package com.crosspaste.ui.paste

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.ui.model.PasteDataViewModel
import com.crosspaste.ui.paste.preview.PasteEmptyScreenView
import com.crosspaste.ui.paste.preview.PastePreviewItemView
import com.crosspaste.ui.paste.preview.PasteSpecificPreviewView
import com.crosspaste.ui.paste.preview.PasteToTopView
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteboardContentView(openTopBar: () -> Unit) {
    val pasteDataViewModel = koinInject<PasteDataViewModel>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()

    val listState = rememberLazyListState()
    var isScrolling by remember { mutableStateOf(false) }
    var showToTop by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val rememberPasteDataList by pasteDataViewModel.pasteDataList.collectAsState()
    val showMainWindow by appWindowManager.showMainWindow.collectAsState()

    var previousFirstItemId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        pasteDataViewModel.resume()
        onDispose {
            pasteDataViewModel.cleanup()
        }
    }

    LaunchedEffect(rememberPasteDataList) {
        if (rememberPasteDataList.isNotEmpty()) {
            val currentFirstItemId = rememberPasteDataList.first().id
            if (currentFirstItemId != previousFirstItemId &&
                listState.firstVisibleItemIndex == 1
            ) {
                listState.animateScrollToItem(0)
                showToTop = false
            }
            previousFirstItemId = currentFirstItemId
        }
    }

    LaunchedEffect(showMainWindow) {
        if (showMainWindow) {
            pasteDataViewModel.resume()
            if (rememberPasteDataList.isNotEmpty()) {
                listState.scrollToItem(0)
                showToTop = false
            }
        } else {
            pasteDataViewModel.cleanup()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to
                listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (_, _) ->
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                if (visibleItems.last().index == rememberPasteDataList.size - 1) {
                    pasteDataViewModel.loadMore()
                }
                showToTop = visibleItems.last().index >= 30
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

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 8.dp)
                .padding(vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(5.dp)),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.wrapContentHeight(),
            ) {
                itemsIndexed(
                    rememberPasteDataList,
                    key = { _, item -> item.id },
                ) { index, pasteData ->
                    PastePreviewItemView(pasteData) {
                        PasteSpecificPreviewView(this)
                    }
                    if (index < rememberPasteDataList.size - 1) {
                        Spacer(modifier = Modifier.height(5.dp))
                    }
                }
            }

            if (rememberPasteDataList.isEmpty()) {
                PasteEmptyScreenView()
            }
        }

        VerticalScrollbar(
            modifier =
                Modifier
                    .background(color = Color.Transparent)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                coroutineScope.launch(CoroutineName("ScrollPaste")) {
                                    listState.scrollBy(-delta)
                                }
                            },
                    ),
            adapter = rememberScrollbarAdapter(scrollState = listState),
            style =
                ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 6.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 300,
                    unhoverColor =
                        if (isScrolling) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
                        } else {
                            Color.Transparent
                        },
                    hoverColor = MaterialTheme.colorScheme.onBackground,
                ),
        )

        if (showToTop) {
            PasteToTopView {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        }
    }
}
