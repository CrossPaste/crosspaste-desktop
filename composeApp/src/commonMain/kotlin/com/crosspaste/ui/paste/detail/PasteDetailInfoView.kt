package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.path.PathProvider
import com.crosspaste.ui.base.AppImageIcon
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.noFavorite
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class PasteDetailInfoItem(val key: String, val value: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasteDetailInfoView(
    indexInfo: String? = null,
    pasteData: PasteData,
    items: List<PasteDetailInfoItem>,
) {
    val current = LocalKoinApplication.current
    val iconStyle = current.koin.get<IconStyle>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val pasteDao = current.koin.get<PasteDao>()
    val pathProvider = current.koin.get<PathProvider>()

    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copywriter.getText("information") + (indexInfo?.let { " - $it" } ?: ""),
            style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 14.sp,
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            modifier =
                Modifier.size(15.dp).onClick {
                    pasteDao.setFavorite(pasteData.id, !pasteData.favorite)
                },
            painter = if (pasteData.favorite) favorite() else noFavorite(),
            contentDescription = "Favorite",
            tint = if (pasteData.favorite) Color(0xFFFFCE34) else MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        pasteData.source?.let { source ->

            val iconPath by remember(source) { mutableStateOf(pathProvider.resolve("$source.png", AppFileType.ICON)) }

            val iconExist by remember(source) {
                mutableStateOf(iconPath.toFile().exists())
            }

            if (iconExist) {
                val isMacStyleIcon by remember(source) { mutableStateOf(iconStyle.isMacStyleIcon(source)) }
                AppImageIcon(iconPath, isMacStyleIcon, 30.dp)
            }

            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = source,
                style =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 12.sp,
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
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            ),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = item.value,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 12.sp,
                            ),
                    )
                }
                if (index != items.size - 1) {
                    Divider(
                        modifier = Modifier.fillMaxWidth().height(1.dp),
                        thickness = 2.dp,
                    )
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
                    unhoverColor = if (isScrolling) MaterialTheme.colors.onBackground.copy(alpha = 0.48f) else Color.Transparent,
                    hoverColor = MaterialTheme.colors.onBackground,
                ),
        )
    }
}
