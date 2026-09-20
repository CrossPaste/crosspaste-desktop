package com.crosspaste.ui.paste.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.FrameWindowScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.getIconData
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.AppSourceIcon
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.model.PastePanelViewModel
import com.crosspaste.ui.paste.PasteEmptyScreenView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.zeroRoundedCornerShape
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun FrameWindowScope.PastePanelContent(
    transparent: Boolean,
    onClose: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val viewModel = koinInject<PastePanelViewModel>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val items by viewModel.items.collectAsState()
    val nextIndex by viewModel.nextIndex.collectAsState()
    val loadAll by viewModel.loadAll.collectAsState()

    val listState = rememberLazyListState()

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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(background),
    ) {
        WindowDraggableArea {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(appSizeValue.pastePanelHeaderHeight)
                        .padding(start = medium, end = small2X),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copywriter.getText("paste_panel"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PasteIconButton(
                    size = large2X,
                    onClick = onClose,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = "close",
                        modifier = Modifier.size(large2X),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        HorizontalDivider()

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
