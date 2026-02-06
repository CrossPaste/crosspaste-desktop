package com.crosspaste.ui.paste.side

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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.model.RequestPasteListFocus
import com.crosspaste.ui.paste.PasteEmptyScreenView
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.paste.side.preview.SidePreviewView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.event.KeyEvent.VK_1
import java.awt.event.KeyEvent.VK_9

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePasteboardContentView() {
    val pasteMenuService = koinInject<DesktopPasteMenuService>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val searchWindowInfo = LocalSearchWindowInfoState.current
    val inputSearch by pasteSearchViewModel.inputSearch.collectAsState()
    val searchBaseParams by pasteSearchViewModel.searchBaseParams.collectAsState()
    val searchResult by pasteSearchViewModel.searchResults.collectAsState()
    val selectedIndexes by pasteSelectionViewModel.selectedIndexes.collectAsState()

    val searchListState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(scrollState = searchListState)
    var showScrollbar by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val latestSearchResult = rememberUpdatedState(searchResult)
    val pasteListFocusRequester = remember { FocusRequester() }
    var previousFirstItemId by remember { mutableStateOf<Long?>(null) }
    var isShiftPressed by remember { mutableStateOf(false) }
    var isCtrlPressed by remember { mutableStateOf(false) }

    val showToStart by remember {
        derivedStateOf {
            // Show "to start" button when scrolled beyond three screens
            searchListState.firstVisibleItemIndex >= searchListState.layoutInfo.visibleItemsInfo.size * 3
        }
    }

    LaunchedEffect(
        searchWindowInfo.show,
        inputSearch,
        searchBaseParams.favorite,
        searchBaseParams.sort,
        searchBaseParams.pasteType,
    ) {
        pasteSelectionViewModel.initSelectIndex()
        delay(32)
        searchListState.animateScrollToItem(0)
        if (searchWindowInfo.show) {
            isCtrlPressed = false
            isShiftPressed = false
        }
    }

    LaunchedEffect(selectedIndexes) {
        val visibleItems = searchListState.layoutInfo.visibleItemsInfo
        val viewportStartOffset = searchListState.layoutInfo.viewportStartOffset
        val viewportEndOffset = searchListState.layoutInfo.viewportEndOffset

        if (visibleItems.isEmpty()) return@LaunchedEffect

        val itemSpacing = visibleItems[0].size

        val selectedIndex = selectedIndexes.firstOrNull() ?: return@LaunchedEffect

        visibleItems.find { it.index == selectedIndex }?.let { selectedItem ->
            val itemStart = selectedItem.offset
            val itemEnd = selectedItem.offset + selectedItem.size

            val isFullyVisible = itemStart >= 0 && itemEnd <= (viewportEndOffset - viewportStartOffset)

            val isPartiallyVisibleAtTop = itemStart < 0 && itemEnd > 0
            val isPartiallyVisibleAtBottom =
                viewportEndOffset - viewportStartOffset in (itemStart + 1)..<itemEnd

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

    LaunchedEffect(searchListState) {
        snapshotFlow {
            searchListState.layoutInfo.visibleItemsInfo
        }.distinctUntilChanged().collect { visibleItems ->
            if (visibleItems.isNotEmpty()) {
                // Prepare content within 2 times the screen
                if (latestSearchResult.value.size - visibleItems.last().index < visibleItems.size) {
                    pasteSearchViewModel.tryAddLimit()
                }
            }

            showScrollbar = latestSearchResult.value.size > visibleItems.size
            if (showScrollbar) {
                scrollJob?.cancel()
                scrollJob =
                    coroutineScope.launch {
                        delay(1000)
                        showScrollbar = false
                    }
            }
        }
    }

    LaunchedEffect(searchResult) {
        if (searchResult.isNotEmpty()) {
            val currentFirstItemId = searchResult.first().id

            if (currentFirstItemId != previousFirstItemId) {
                snapshotFlow { searchListState.layoutInfo }
                    .take(2)
                    .collect { layoutInfo ->
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (searchResult.size > visibleItems.size) {
                            showScrollbar = true
                        }

                        if (searchListState.firstVisibleItemIndex <= 1) {
                            searchListState.animateScrollToItem(0)
                        }
                    }
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
                .padding(bottom = appSizeValue.sideSearchPaddingSize),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(pasteListFocusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        isShiftPressed = keyEvent.isShiftPressed
                        isCtrlPressed = keyEvent.isCtrlPressed

                        if (keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.key.nativeKeyCode in VK_1..VK_9) {
                                if (isCtrlPressed) {
                                    mainCoroutineDispatcher.launch {
                                        val index = keyEvent.key.nativeKeyCode - VK_1
                                        if (searchResult.size > index) {
                                            pasteSelectionViewModel.toPaste(searchResult[index])
                                        }
                                    }
                                }
                            }
                        }

                        false
                    },
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

                    val scope =
                        remember(
                            currentPasteData.id,
                            currentPasteData.pasteState,
                            currentPasteData.pasteSearchContent,
                        ) {
                            createPasteDataScope(currentPasteData)
                        }

                    scope?.let {
                        Spacer(modifier = Modifier.width(appSizeValue.sideSearchPaddingSize))
                        scope.SidePasteItemView(
                            selected = currentIndex in selectedIndexes,
                            onPress = {
                                pasteSelectionViewModel.clickSelectedIndex(currentIndex, isShiftPressed)
                            },
                            onDoubleTap = {
                                if (!isShiftPressed) {
                                    pasteMenuService.quickPasteFromSearchWindow(currentPasteData)
                                }
                            },
                        ) {
                            scope.SidePreviewView(
                                showTop9 = isCtrlPressed && currentIndex < 9,
                                index = currentIndex,
                            )
                        }
                    }

                    if (index == searchResult.size - 1) {
                        Spacer(modifier = Modifier.width(appSizeValue.sideSearchPaddingSize))
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
