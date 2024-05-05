package com.clipevery.ui.clip.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.path.PathProvider
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.LoadingStateData
import com.clipevery.ui.base.image
import com.clipevery.ui.base.starRegular
import com.clipevery.ui.base.starSolid
import com.clipevery.utils.getResourceUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

data class ClipDetailInfoItem(val key: String, val value: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipDetailInfoView(
    indexInfo: String? = null,
    clipData: ClipData,
    items: List<ClipDetailInfoItem>,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val clipDao = current.koin.get<ClipDao>()
    val pathProvider = current.koin.get<PathProvider>()

    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copywriter.getText("Information") + (indexInfo?.let { " - $it" } ?: ""),
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
                    clipDao.setFavorite(clipData.id, !clipData.favorite)
                },
            painter = if (clipData.favorite) starSolid() else starRegular(),
            contentDescription = "Favorite",
            tint = if (clipData.favorite) Color(0xFFFFCE34) else MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        clipData.source?.let { source ->
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
            Spacer(modifier = Modifier.width(8.dp))
            AsyncView(
                key = clipData.id,
                load = {
                    val path = pathProvider.resolve("$source.png", AppFileType.ICON)
                    LoadImageData(path, getResourceUtils().loadPainter(path, density))
                },
            ) { loadImageData ->
                when (loadImageData) {
                    is LoadImageData -> {
                        Image(
                            modifier = Modifier.size(32.dp),
                            painter = loadImageData.toPainterImage.toPainter(),
                            contentDescription = "app icon",
                        )
                    }

                    is LoadingStateData -> {
                        Image(
                            modifier = Modifier.size(32.dp),
                            painter = image(),
                            contentDescription = "loading app icon",
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    val listState = rememberLazyListState()
    val adapter = rememberScrollbarAdapter(scrollState = listState)
    val showScrollbar by remember { mutableStateOf(items.size > 4) }
    val coroutineScope = rememberCoroutineScope()

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
                                coroutineScope.launch(CoroutineName("ScrollClip")) {
                                    listState.scrollBy(-delta)
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
