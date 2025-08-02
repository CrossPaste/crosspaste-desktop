package com.crosspaste.ui.paste

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.model.RequestPasteListFocus
import com.crosspaste.ui.paste.preview.PasteEmptyScreenView
import com.crosspaste.ui.paste.side.SidePastePreviewItemView
import com.crosspaste.ui.paste.side.ToStartView
import com.crosspaste.ui.paste.side.preview.SidePreviewView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePasteboardContentView() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteMenuService = koinInject<DesktopPasteMenuService>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val searchListState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(scrollState = searchListState)
    var showScrollbar by remember { mutableStateOf(false) }
    var showToStart by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    val inputSearch by pasteSearchViewModel.inputSearch.collectAsState()

    val searchFavorite by pasteSearchViewModel.searchFavorite.collectAsState()

    val searchSort by pasteSearchViewModel.searchSort.collectAsState()

    val searchPasteType by pasteSearchViewModel.searchPasteType.collectAsState()

    val searchResult by pasteSearchViewModel.searchResults.collectAsState()

    val selectedIndex by pasteSelectionViewModel.selectedIndex.collectAsState()

    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val latestSearchResult = rememberUpdatedState(searchResult)

    val pasteListFocusRequester = remember { FocusRequester() }

    var previousFirstItemId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(showSearchWindow, inputSearch, searchFavorite, searchSort, searchPasteType) {
        if (showSearchWindow) {
            pasteSelectionViewModel.initSelectIndex()
            delay(32)
            searchListState.animateScrollToItem(0)
            showToStart = false
        }
    }

    LaunchedEffect(selectedIndex, showSearchWindow) {
        if (showSearchWindow) {
            val visibleItems = searchListState.layoutInfo.visibleItemsInfo
            val viewportStartOffset = searchListState.layoutInfo.viewportStartOffset
            val viewportEndOffset = searchListState.layoutInfo.viewportEndOffset

            if (visibleItems.isEmpty()) return@LaunchedEffect

            val itemSpacing = visibleItems[0].size

            visibleItems.find { it.index == selectedIndex }?.let { selectedItem ->
                val itemStart = selectedItem.offset
                val itemEnd = selectedItem.offset + selectedItem.size

                val isFullyVisible = itemStart >= 0 && itemEnd <= (viewportEndOffset - viewportStartOffset)

                val isPartiallyVisibleAtTop = itemStart < 0 && itemEnd > 0
                val isPartiallyVisibleAtBottom =
                    itemStart < (viewportEndOffset - viewportStartOffset) &&
                        itemEnd > (viewportEndOffset - viewportStartOffset)

                when {
                    isFullyVisible -> {
                    }
                    isPartiallyVisibleAtTop -> {
                        searchListState.animateScrollBy(-itemSpacing.toFloat())
                    }
                    isPartiallyVisibleAtBottom -> {
                        searchListState.animateScrollBy(itemSpacing.toFloat())
                    }
                }
            } ?: run {
                val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
                val firstVisibleIndex = visibleItems.firstOrNull()?.index ?: 0

                when {
                    selectedIndex > lastVisibleIndex -> {
                        searchListState.animateScrollBy(
                            (selectedIndex - lastVisibleIndex) * itemSpacing.toFloat(),
                        )
                    }
                    selectedIndex < firstVisibleIndex -> {
                        searchListState.animateScrollBy(
                            (selectedIndex - firstVisibleIndex) * itemSpacing.toFloat(),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(searchListState) {
        snapshotFlow {
            searchListState.firstVisibleItemIndex to searchListState.layoutInfo.visibleItemsInfo
        }.distinctUntilChanged().collect { (_, visibleItems) ->
            if (visibleItems.isNotEmpty()) {
                if (latestSearchResult.value.size - visibleItems.last().index <= 10) {
                    pasteSearchViewModel.tryAddLimit()
                }
                showToStart = visibleItems.last().index >= 30
            }

            val shouldShowScrollbar = latestSearchResult.value.size > 10
            if (shouldShowScrollbar) {
                scrollJob?.cancel()
                scrollJob =
                    coroutineScope.launch(CoroutineName("HiddenScroll")) {
                        delay(500)
                        showScrollbar = false
                    }
            }
            showScrollbar = shouldShowScrollbar
        }
    }

    LaunchedEffect(searchResult) {
        if (searchResult.isNotEmpty()) {
            val visibleItems = searchListState.layoutInfo.visibleItemsInfo
            if (searchResult.size > visibleItems.size) {
                showScrollbar = true
            }

            val currentFirstItemId = searchResult.first().id
            if (currentFirstItemId != previousFirstItemId &&
                searchListState.firstVisibleItemIndex == 1
            ) {
                searchListState.animateScrollToItem(0)
                showToStart = false
            }
            previousFirstItemId = currentFirstItemId
        }
    }

    LaunchedEffect(Unit) {
        pasteSelectionViewModel.uiEvent.collect { event ->
            when (event) {
                RequestPasteListFocus -> {
                    if (pasteListFocusRequester.requestFocus()) {
                        pasteSelectionViewModel.setFocusedElement(
                            FocusedElement.PASTE_LIST,
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(vertical = medium),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(pasteListFocusRequester)
                    .focusable(),
            contentAlignment = Alignment.CenterStart,
        ) {
            LazyRow(
                state = searchListState,
                modifier =
                    Modifier
                        .wrapContentWidth()
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val change = event.changes.first()
                            val scrollDelta = change.scrollDelta
                            coroutineScope.launch {
                                searchListState.scrollBy(scrollDelta.y * 50f)
                            }
                        },
            ) {
                itemsIndexed(
                    searchResult,
                    key = { _, item -> item.id },
                ) { index, pasteData ->

                    val currentIndex by rememberUpdatedState(index)
                    val currentPasteData by rememberUpdatedState(pasteData)

                    Spacer(modifier = Modifier.width(medium))
                    SidePastePreviewItemView(
                        pasteData = currentPasteData,
                        selected = index == selectedIndex,
                        onPress = {
                            pasteSelectionViewModel.clickSelectedIndex(currentIndex)
                        },
                        onDoubleTap = {
                            pasteMenuService.quickPasteFromSearchWindow(currentPasteData)
                        },
                    ) {
                        SidePreviewView(this)
                    }

                    if (index == searchResult.size - 1) {
                        Spacer(modifier = Modifier.width(medium))
                    }
                }
            }

            if (searchResult.isEmpty()) {
                PasteEmptyScreenView()
            }

            HorizontalScrollbar(
                modifier =
                    Modifier
                        .background(color = Color.Transparent)
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .offset(y = medium)
                        .draggable(
                            orientation = Orientation.Horizontal,
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
                        minimalHeight = medium,
                        thickness = tiny2X,
                        shape = tiny3XRoundedCornerShape,
                        hoverDurationMillis = 300,
                        unhoverColor =
                            if (showScrollbar) {
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

            if (showToStart) {
                ToStartView {
                    coroutineScope.launch {
                        searchListState.animateScrollToItem(0)
                    }
                }
            }
        }
    }
}
