package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PastePreviewService
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.toTop
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PastePreviewsView() {
    val current = LocalKoinApplication.current
    val pastePreviewService = current.koin.get<PastePreviewService>()
    val appWindowManager = current.koin.get<AppWindowManager>()

    val listState = rememberLazyListState()
    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val rememberPasteDataList = remember(pastePreviewService.refreshTime) { pastePreviewService.pasteDataList }

    var showToTop by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        coroutineScope.launch {
            pastePreviewService.loadPastePreviewList(false)
        }
        onDispose {
            coroutineScope.launch {
                pastePreviewService.clearData()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (rememberPasteDataList.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(appWindowManager.showMainWindow) {
        if (appWindowManager.showMainWindow) {
            if (rememberPasteDataList.isNotEmpty()) {
                listState.scrollToItem(0)
            }
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
                    pastePreviewService.loadPastePreviewList(force = true, toLoadMore = true)
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
                .background(color = MaterialTheme.colors.surface.copy(alpha = 0.12f)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.wrapContentHeight(),
        ) {
            itemsIndexed(
                rememberPasteDataList,
                key = { _, item -> item.id },
            ) { _, pasteData ->
                PastePreviewItemView(pasteData) {
                    PasteSpecificPreviewView(this)
                }
            }
        }

        if (rememberPasteDataList.isEmpty()) {
            EmptyScreenView()
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

        if (showToTop) {
            ToTop {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        }
    }
}

@Composable
fun EmptyScreenView() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f),
                textAlign = TextAlign.Center,
                text = copywriter.getText("Haven't_listened_to_any_pasteboard_data_yet"),
                maxLines = 3,
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToTop(toTopAction: () -> Unit) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Row(
        modifier =
            Modifier.fillMaxSize()
                .padding(end = 30.dp, bottom = 30.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        var transparency by remember { mutableStateOf(0.5f) }

        Spacer(modifier = Modifier.weight(1f))
        PasteTooltipAreaView(
            text = copywriter.getText("Scroll_to_top"),
            delayMillis = 1000,
        ) {
            PasteIconButton(
                size = 40.dp,
                onClick = {
                    toTopAction()
                },
                modifier =
                    Modifier
                        .background(MaterialTheme.colors.surface.copy(alpha = transparency), CircleShape)
                        .onPointerEvent(
                            eventType = PointerEventType.Enter,
                            onEvent = {
                                transparency = 1.0f
                            },
                        )
                        .onPointerEvent(
                            eventType = PointerEventType.Exit,
                            onEvent = {
                                transparency = 0.5f
                            },
                        ),
            ) {
                Icon(
                    painter = toTop(),
                    contentDescription = "To Top",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colors.primary.copy(alpha = transparency),
                )
            }
        }
    }
}
