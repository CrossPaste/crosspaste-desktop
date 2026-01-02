package com.crosspaste.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalSettingsScrollState = compositionLocalOf<ScrollState?> { null }

@Composable
fun BoxScope.SettingsContentView() {
    val lazyListState = rememberLazyListState()

    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex + lazyListState.firstVisibleItemScrollOffset }.collect {
            isScrolling = true
            scrollJob?.cancel()
            scrollJob =
                coroutineScope.launch(CoroutineName("HiddenScroll")) {
                    delay(500)
                    isScrolling = false
                }
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SectionHeader("general")
        }

        item {
            MainSettingsContentView()
        }

        item {
            SectionHeader("advanced", topPadding = medium)
        }

        item {
            AdvancedSettingsContentView()
        }
    }

    VerticalScrollbar(
        modifier =
            Modifier
                .background(color = Color.Transparent)
                .fillMaxHeight()
                .offset(x = medium)
                .padding(end = tiny3X)
                .align(Alignment.CenterEnd)
                .draggable(
                    orientation = Orientation.Vertical,
                    state =
                        rememberDraggableState { delta ->
                            coroutineScope.launch(CoroutineName("ScrollPaste")) {
                                lazyListState.scrollBy(-delta)
                            }
                        },
                ),
        adapter = rememberScrollbarAdapter(lazyListState),
        style =
            ScrollbarStyle(
                minimalHeight = medium,
                thickness = tiny,
                shape = tiny3XRoundedCornerShape,
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
