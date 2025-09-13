package com.crosspaste.ui.paste.preview

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.ui.model.PasteDataViewModel
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteboardContentView() {
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
            Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(end = medium)
                    .clip(tinyRoundedCornerShape),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.wrapContentHeight(),
            ) {
                itemsIndexed(
                    rememberPasteDataList,
                    key = { _, item -> item.id },
                ) { index, pasteData ->
                    val currentIndex by rememberUpdatedState(index)
                    val currentPasteData by rememberUpdatedState(pasteData)

                    val scope =
                        remember(currentPasteData.id, currentPasteData.pasteState) {
                            createPasteDataScope(currentPasteData)
                        }

                    scope?.let {
                        scope.PastePreviewItemView {
                            PasteItemView()
                        }
                        if (currentIndex < rememberPasteDataList.size - 1) {
                            Spacer(modifier = Modifier.height(small3X))
                        }
                    }
                }
            }

            if (rememberPasteDataList.isEmpty()) {
                PasteEmptyScreenView()
            }

            if (showToTop) {
                PasteToTopView {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            }
        }

        VerticalScrollbar(
            modifier =
                Modifier
                    .background(color = Color.Transparent)
                    .fillMaxHeight()
                    .padding(end = tiny3X)
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
                    minimalHeight = medium,
                    thickness = tiny,
                    shape = tiny3XRoundedCornerShape,
                    hoverDurationMillis = 300,
                    unhoverColor =
                        if (isScrolling) {
                            MaterialTheme.colorScheme
                                .contentColorFor(
                                    AppUIColors.appBackground,
                                ).copy(alpha = 0.48f)
                        } else {
                            Color.Transparent
                        },
                    hoverColor =
                        MaterialTheme.colorScheme.contentColorFor(
                            AppUIColors.appBackground,
                        ),
                ),
        )
    }
}
