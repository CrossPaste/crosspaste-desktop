package com.crosspaste.ui.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.icon.FaviconLoader
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.path.PathProvider
import com.crosspaste.ui.base.AppImageIcon
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.base.LoadIconData
import com.crosspaste.ui.base.LoadImageData
import com.crosspaste.ui.base.ToPainterImage
import com.crosspaste.ui.paste.PasteTypeIconBaseView
import com.crosspaste.ui.paste.preview.getPasteItem
import com.crosspaste.ui.paste.title.getPasteTitle
import com.crosspaste.ui.selectColor
import com.crosspaste.utils.getPainterUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okio.FileSystem

@Composable
fun SearchListView(setSelectedIndex: (Int) -> Unit) {
    val current = LocalKoinApplication.current
    val pasteSearchService = current.koin.get<PasteSearchService>()
    val appWindowManager = current.koin.get<AppWindowManager>()
    val searchListState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(scrollState = searchListState)
    var showScrollbar by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }

    val coroutineScope = rememberCoroutineScope()

    val searchResult = remember(pasteSearchService.searchTime) { pasteSearchService.searchResult }

    LaunchedEffect(appWindowManager.showSearchWindow) {
        if (appWindowManager.showSearchWindow) {
            if (pasteSearchService.searchResult.size > 0) {
                pasteSearchService.selectedIndex = 0
                searchListState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(pasteSearchService.selectedIndex, appWindowManager.showSearchWindow) {
        if (appWindowManager.showSearchWindow) {
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
                    unhoverColor = if (showScrollbar) MaterialTheme.colors.onBackground.copy(alpha = 0.48f) else Color.Transparent,
                    hoverColor = MaterialTheme.colors.onBackground,
                ),
        )
    }
}

@Composable
fun PasteTitleView(
    pasteData: PasteData,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title by remember(pasteData.pasteState) { mutableStateOf(getPasteTitle(pasteData)) }

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
                        .background(if (selected) MaterialTheme.colors.selectColor() else MaterialTheme.colors.background)
                        .clickable { onClick() },
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PasteTypeIconView(pasteData)

                    Text(
                        modifier = Modifier.padding(start = 10.dp),
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

@Composable
fun PasteTypeIconView(
    pasteData: PasteData,
    padding: Dp = 2.dp,
    size: Dp = 20.dp,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val iconStyle = current.koin.get<IconStyle>()
    val pathProvider = current.koin.get<PathProvider>()
    val faviconLoader = current.koin.get<FaviconLoader>()
    val loadIconData =
        LoadIconData(
            pasteData.pasteType,
            object : ToPainterImage {
                @Composable
                override fun toPainter(): Painter {
                    return PasteTypeIconBaseView(pasteData.pasteType)
                }
            },
        )

    if (pasteData.pasteType == PasteType.URL) {
        AsyncView(
            key = pasteData.id,
            defaultValue = loadIconData,
            load = {
                pasteData.getPasteItem()?.let {
                    it as PasteUrl
                    try {
                        faviconLoader.load(it.url)?.let { path ->
                            return@AsyncView LoadImageData(path, getPainterUtils().loadPainter(path, density))
                        }
                    } catch (ignore: Exception) {
                    }
                }
                loadIconData
            },
        ) { loadView ->
            when (loadView) {
                is LoadIconData -> {
                    Icon(
                        painter = loadView.toPainterImage.toPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(padding).size(size),
                        tint = MaterialTheme.colors.onBackground,
                    )
                }
                is LoadImageData -> {
                    Image(
                        painter = loadView.toPainterImage.toPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(padding).size(size),
                    )
                }
            }
        }
    } else if (pasteData.pasteType != PasteType.HTML) {
        Icon(
            painter = loadIconData.toPainterImage.toPainter(),
            contentDescription = "Paste Icon",
            modifier = Modifier.padding(padding).size(size),
            tint = MaterialTheme.colors.onBackground,
        )
    } else {
        pasteData.source?.let {
            val path = pathProvider.resolve("$it.png", AppFileType.ICON)
            if (FileSystem.SYSTEM.exists(path)) {
                val isMacStyleIcon by remember(it) { mutableStateOf(iconStyle.isMacStyleIcon(it)) }
                AppImageIcon(path = path, isMacStyleIcon = isMacStyleIcon, size = size + 2.dp)
            } else {
                Icon(
                    painter = loadIconData.toPainterImage.toPainter(),
                    contentDescription = "Paste Icon",
                    modifier = Modifier.padding(padding).size(size),
                    tint = MaterialTheme.colors.onBackground,
                )
            }
        } ?: run {
            Icon(
                painter = loadIconData.toPainterImage.toPainter(),
                contentDescription = "Paste Icon",
                modifier = Modifier.padding(padding).size(size),
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}
