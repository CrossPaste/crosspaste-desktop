package com.crosspaste.ui.paste.panel

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.FrameWindowScope
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.getIconData
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.AppSourceIcon
import com.crosspaste.ui.model.PastePanelViewModel
import com.crosspaste.ui.paste.PasteEmptyScreenView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.zeroRoundedCornerShape
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

private val SCROLLBAR_LINGER = 1000.milliseconds

@Composable
fun FrameWindowScope.PastePanelContent(transparent: Boolean) {
    val viewModel = koinInject<PastePanelViewModel>()

    val items by viewModel.items.collectAsState()
    val nextIndex by viewModel.nextIndex.collectAsState()
    val loadAll by viewModel.loadAll.collectAsState()

    val listState = rememberLazyListState()

    // Like the search window: the bar shows while the list moves and fades out after
    // a pause, but its track and thumb always report how much is loaded and where
    // the viewport is, so hovering the edge reveals it at any time.
    var scrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                scrolling = true
                delay(SCROLLBAR_LINGER)
                scrolling = false
            }
    }

    // Ask for the next page once the tail comes into view
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(loadAll) {
        if (!loadAll) {
            snapshotFlow { nearEnd }.collect { if (it) viewModel.loadMore() }
        }
    }

    val background =
        if (transparent) {
            AppUIColors.generalBackground.copy(alpha = 0.6f)
        } else {
            AppUIColors.generalBackground
        }
    val shape = if (transparent) small2XRoundedCornerShape else zeroRoundedCornerShape

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(background),
    ) {
        if (items.isEmpty()) {
            PasteEmptyScreenView()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items,
                    key = { _, item -> item.id },
                    contentType = { _, item -> item.pasteType },
                ) { index, pasteData ->
                    PastePanelRow(
                        pasteData = pasteData,
                        highlighted = index == nextIndex,
                        onClick = {
                            mainCoroutineDispatcher.launch {
                                viewModel.paste(pasteData)
                            }
                        },
                    )
                }
            }

            VerticalScrollbar(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = tiny3X, horizontal = tiny4X),
                adapter = rememberScrollbarAdapter(listState),
                style =
                    ScrollbarStyle(
                        minimalHeight = medium,
                        thickness = tiny2X,
                        shape = tiny3XRoundedCornerShape,
                        hoverDurationMillis = 300,
                        unhoverColor =
                            if (scrolling) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                            } else {
                                Color.Transparent
                            },
                        hoverColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}

@Composable
private fun PastePanelRow(
    pasteData: PasteData,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDataHelper = koinInject<PasteDataHelper>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val typeName = copywriter.getText(pasteData.getTypeName())
    val title =
        remember(pasteData.id, pasteData.hash, pasteData.pasteState, typeName) {
            pasteData.pasteAppearItem?.getUserEditName()
                ?: pasteDataHelper
                    .getSummary(pasteData, typeName, typeName)
                    .replace(WHITESPACE_RUN, " ")
                    .trim()
                    .ifEmpty { typeName }
        }

    val rowBackground =
        if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSizeValue.pastePanelRowHeight)
                .background(rowBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PastePanelRowIcon(pasteData)
        Spacer(modifier = Modifier.width(small2X))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PastePanelRowIcon(pasteData: PasteData) {
    val iconData = pasteData.getType().getIconData()

    val typeIcon: @Composable () -> Unit = {
        Icon(
            imageVector = iconData.imageVector,
            contentDescription = "type",
            modifier = Modifier.size(xLarge),
            tint = iconData.color,
        )
    }

    val source = pasteData.source
    if (source != null) {
        AppSourceIcon(
            source = source,
            appInstanceId = pasteData.appInstanceId,
            size = xLarge,
            defaultIcon = typeIcon,
        )
    } else {
        Box(modifier = Modifier.size(xLarge), contentAlignment = Alignment.Center) {
            typeIcon()
        }
    }
}

private val WHITESPACE_RUN = Regex("\\s+")
