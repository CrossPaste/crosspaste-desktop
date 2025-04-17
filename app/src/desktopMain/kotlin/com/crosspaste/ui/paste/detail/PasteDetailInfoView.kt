package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.AppSourceIcon
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.noFavorite
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class PasteDetailInfoItem(val key: String, val value: String)

@Composable
fun PasteDetailInfoView(
    indexInfo: String? = null,
    pasteData: PasteData,
    items: List<PasteDetailInfoItem>,
) {
    val appControl = koinInject<AppControl>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()

    var favorite by remember(pasteData.id) {
        mutableStateOf(pasteData.favorite)
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copywriter.getText("information") + (indexInfo?.let { " - $it" } ?: ""),
            color = MaterialTheme.colorScheme.onSurface,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    lineHeight = TextUnit.Unspecified,
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        PasteTooltipIconView(
            painter = if (favorite) favorite() else noFavorite(),
            contentDescription = "Favorite",
            tint = MaterialTheme.colorScheme.primary,
            text = copywriter.getText("whether_to_search_only_favorites"),
        ) {
            if (appControl.isFavoriteEnabled()) {
                pasteDao.setFavorite(pasteData.id, !favorite)
                favorite = !favorite
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        pasteData.source?.let { source ->

            AppSourceIcon(
                pasteData = pasteData,
                source = source,
                iconColor = MaterialTheme.colorScheme.primary,
                size = 30.dp,
            )

            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = source,
                color = MaterialTheme.colorScheme.onSurface,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Light,
                        lineHeight = TextUnit.Unspecified,
                    ),
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    val listState = rememberLazyListState()
    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to
                listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (_, _) ->
            isScrolling = true
            scrollJob?.cancel()
            scrollJob =
                coroutineScope.launch(CoroutineName("HiddenScroll")) {
                    delay(500)
                    isScrolling = false
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.key },
            ) { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = copywriter.getText(item.key),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = TextUnit.Unspecified,
                            ),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = item.value,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                                lineHeight = TextUnit.Unspecified,
                            ),
                    )
                }
                if (index != items.size - 1) {
                    HorizontalDivider(thickness = 1.5.dp)
                }
            }
        }

        VerticalScrollbar(
            modifier =
                Modifier.offset(x = 10.dp).background(color = Color.Transparent)
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
                    unhoverColor =
                        if (isScrolling) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
                        } else {
                            Color.Transparent
                        },
                    hoverColor = MaterialTheme.colorScheme.onBackground,
                ),
        )
    }
}
