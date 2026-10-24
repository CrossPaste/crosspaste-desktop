package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.model.PluginActionItem
import com.crosspaste.ui.model.PluginActionsPanel
import com.crosspaste.ui.model.PluginActionsViewModel
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import org.koin.compose.koinInject

private val PANEL_WIDTH = 240.dp

/** A row of the panel's list: a plugin name above its actions, or a runnable item. */
private sealed interface PanelRow {
    data class Header(
        val name: String,
    ) : PanelRow

    data class Item(
        val index: Int,
        val item: PluginActionItem,
    ) : PanelRow
}

private fun panelRows(items: List<PluginActionItem>): List<PanelRow> =
    buildList {
        var group: String? = null
        items.forEachIndexed { index, item ->
            if (item.group != null && item.group != group) {
                add(PanelRow.Header(item.group))
            }
            group = item.group
            add(PanelRow.Item(index, item))
        }
    }

/**
 * The plugin panel over the search results, beside the selected card. A click
 * outside the panel closes it; the keys are handled by the window content.
 */
@Composable
fun PluginActionsPanelView() {
    val pluginActionsViewModel = koinInject<PluginActionsViewModel>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val appSizeValue = LocalDesktopAppSizeValueState.current
    val density = LocalDensity.current

    val panel by pluginActionsViewModel.panel.collectAsState()
    val current = panel ?: return

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { pluginActionsViewModel.close() },
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = appSizeValue.sideSearchTopBarHeight,
                        bottom = appSizeValue.sideSearchPaddingSize,
                    ),
        ) {
            // Line up with the selected card when it is on screen, but keep the panel inside the window
            val cardOffset =
                pasteSelectionViewModel.searchListState
                    ?.layoutInfo
                    ?.visibleItemsInfo
                    ?.firstOrNull { it.key == current.pasteData.id }
                    ?.let { with(density) { it.offset.toDp() } + appSizeValue.sideSearchPaddingSize }
                    ?: appSizeValue.sideSearchPaddingSize
            val maxOffset = (maxWidth - PANEL_WIDTH - appSizeValue.sideSearchPaddingSize).coerceAtLeast(0.dp)
            PluginActionsCard(
                panel = current,
                modifier =
                    Modifier
                        .offset(x = cardOffset.coerceIn(0.dp, maxOffset))
                        .width(PANEL_WIDTH)
                        .heightIn(max = maxHeight),
                onSelect = pluginActionsViewModel::select,
                onRun = pluginActionsViewModel::runSelected,
            )
        }
    }
}

@Composable
private fun PluginActionsCard(
    panel: PluginActionsPanel,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
    onRun: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val rows = remember(panel.items) { panelRows(panel.items) }
    val listState = rememberLazyListState()

    LaunchedEffect(panel.selectedIndex) {
        val rowIndex = rows.indexOfFirst { it is PanelRow.Item && it.index == panel.selectedIndex }
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible =
            visible.any { info ->
                info.index == rowIndex &&
                    info.offset >= listState.layoutInfo.viewportStartOffset &&
                    info.offset + info.size <= listState.layoutInfo.viewportEndOffset
            }
        if (rowIndex >= 0 && !fullyVisible) {
            listState.scrollToItem(rowIndex)
        }
    }

    Surface(
        modifier =
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        shape = tinyRoundedCornerShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = tiny,
    ) {
        Column(modifier = Modifier.padding(vertical = tiny2X)) {
            if (panel.items.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = small2X, vertical = tiny),
                    text = copywriter.getText("plugin_none_for_item"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    state = listState,
                ) {
                    items(rows) { row ->
                        when (row) {
                            is PanelRow.Header -> PanelHeader(row.name)
                            is PanelRow.Item ->
                                PanelItem(
                                    item = row.item,
                                    selected = row.index == panel.selectedIndex,
                                    running = panel.running && row.index == panel.selectedIndex,
                                    onClick = {
                                        onSelect(row.index)
                                        onRun()
                                    },
                                )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(start = small2X, end = small2X, top = tiny2X),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyboardView(key = enter)
                    Spacer(modifier = Modifier.width(tiny3X))
                    Text(
                        text = copywriter.getText("plugin_run"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(name: String) {
    Text(
        modifier = Modifier.padding(start = small2X, end = small2X, top = tiny, bottom = tiny3X),
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PanelItem(
    item: PluginActionItem,
    selected: Boolean,
    running: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(xxLarge)
                .padding(horizontal = tiny3X)
                .clip(tiny3XRoundedCornerShape)
                .background(background)
                .clickable(onClick = onClick)
                .padding(start = if (item.group != null) small2X + tiny else tiny, end = tiny),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(small2X),
                strokeWidth = tiny4X,
                color = textColor,
            )
        }
    }
}
