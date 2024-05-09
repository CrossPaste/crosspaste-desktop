package com.clipevery.ui.search

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppFileType
import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.item.ClipUrl
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.PathProvider
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.LoadIconData
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.ToPainterImage
import com.clipevery.ui.clip.ClipTypeIconView
import com.clipevery.ui.clip.preview.getClipItem
import com.clipevery.ui.clip.title.getClipTitle
import com.clipevery.utils.getFaviconUtils
import com.clipevery.utils.getResourceUtils
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlin.io.path.exists

@Composable
fun SearchListView(setSelectedIndex: (Int) -> Unit) {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()
    val appWindowManager = current.koin.get<AppWindowManager>()
    val logger = current.koin.get<KLogger>()
    val searchListState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(scrollState = searchListState)
    var showScrollbar by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val searchResult = remember(clipSearchService.searchTime) { clipSearchService.searchResult }

    LaunchedEffect(clipSearchService.selectedIndex) {
        try {
            val visibleItems = searchListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty() && appWindowManager.showSearchWindow) {
                if (visibleItems.last().index < clipSearchService.selectedIndex) {
                    searchListState.scrollToItem(clipSearchService.selectedIndex - 9)
                } else if (visibleItems.first().index > clipSearchService.selectedIndex) {
                    searchListState.scrollToItem(clipSearchService.selectedIndex)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "SearchListView LaunchedEffect error" }
        }
    }

    LaunchedEffect(clipSearchService.searchResult.size) {
        if (clipSearchService.searchResult.size > 10) {
            showScrollbar = true
        }
    }

    Box(modifier = Modifier.width(280.dp).height(400.dp)) {
        LazyColumn(
            state = searchListState,
            modifier = Modifier.width(280.dp).height(400.dp),
        ) {
            itemsIndexed(
                searchResult,
                key = { _, item -> item.id },
            ) { index, clipData ->
                ClipTitleView(clipData, index == clipSearchService.selectedIndex) {
                    setSelectedIndex(index)
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
fun ClipTitleView(
    clipData: ClipData,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title by remember(clipData.clipState) { mutableStateOf(getClipTitle(clipData)) }

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
                        .background(if (selected) MaterialTheme.colors.surface else MaterialTheme.colors.background)
                        .clickable { onClick() },
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClipTypeIconView(clipData)

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
fun ClipTypeIconView(clipData: ClipData) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val pathProvider = current.koin.get<PathProvider>()
    val faviconUtils = getFaviconUtils()
    val loadIconData =
        LoadIconData(
            clipData.clipType,
            object : ToPainterImage {
                @Composable
                override fun toPainter(): Painter {
                    return ClipTypeIconView(clipData.clipType)
                }
            },
        )

    if (clipData.clipType == ClipType.URL) {
        AsyncView(
            key = clipData.id,
            defaultValue = loadIconData,
            load = {
                clipData.getClipItem()?.let {
                    it as ClipUrl
                    faviconUtils.getFaviconPath(it.url)?.let { path ->
                        return@AsyncView LoadImageData(path, getResourceUtils().loadPainter(path, density))
                    }
                }
                loadIconData
            },
        ) { loadView ->
            when (loadView) {
                is LoadIconData -> {
                    Icon(
                        painter = loadView.toPainterImage.toPainter(),
                        contentDescription = "Clip Icon",
                        modifier = Modifier.padding(3.dp).size(18.dp),
                        tint = MaterialTheme.colors.onBackground,
                    )
                }
                is LoadImageData -> {
                    Image(
                        painter = loadView.toPainterImage.toPainter(),
                        contentDescription = "Clip Icon",
                        modifier = Modifier.padding(3.dp).size(18.dp),
                    )
                }
            }
        }
    } else if (clipData.clipType != ClipType.HTML) {
        Icon(
            painter = loadIconData.toPainterImage.toPainter(),
            contentDescription = "Clip Icon",
            modifier = Modifier.padding(3.dp).size(18.dp),
            tint = MaterialTheme.colors.onBackground,
        )
    } else {
        clipData.source?.let {
            val path = pathProvider.resolve("$it.png", AppFileType.ICON)
            if (pathProvider.resolve("$it.png", AppFileType.ICON).exists()) {
                Image(
                    painter = getResourceUtils().loadPainter(path, density).toPainter(),
                    contentDescription = "Clip Icon",
                    modifier = Modifier.padding(3.dp).size(18.dp),
                )
            } else {
                Icon(
                    painter = loadIconData.toPainterImage.toPainter(),
                    contentDescription = "Clip Icon",
                    modifier = Modifier.padding(3.dp).size(18.dp),
                    tint = MaterialTheme.colors.onBackground,
                )
            }
        } ?: run {
            Icon(
                painter = loadIconData.toPainterImage.toPainter(),
                contentDescription = "Clip Icon",
                modifier = Modifier.padding(3.dp).size(18.dp),
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}
