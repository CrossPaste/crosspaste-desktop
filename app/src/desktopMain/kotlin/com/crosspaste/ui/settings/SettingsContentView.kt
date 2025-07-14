package com.crosspaste.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

val LocalSettingsScrollState = compositionLocalOf<ScrollState?> { null }

@Composable
fun SettingsContentView() {
    val settingsViewProvider = koinInject<SettingsViewProvider>()
    val scrollState = rememberScrollState()

    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect {
            isScrolling = true
            scrollJob?.cancel()
            scrollJob =
                coroutineScope.launch(CoroutineName("HiddenScroll")) {
                    delay(500)
                    isScrolling = false
                }
        }
    }

    CompositionLocalProvider(LocalSettingsScrollState provides scrollState) {
        Box(
            modifier =
                Modifier.fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(end = medium)
                        .clip(tinyRoundedCornerShape)
                        .verticalScroll(scrollState),
            ) {
                settingsViewProvider.SettingsCoreView()
            }

            VerticalScrollbar(
                modifier =
                    Modifier
                        .background(color = Color.Transparent)
                        .fillMaxHeight()
                        .padding(end = tiny3X)
                        .align(Alignment.CenterEnd)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state =
                                rememberDraggableState { delta ->
                                    coroutineScope.launch(CoroutineName("ScrollPaste")) {
                                        scrollState.scrollBy(-delta)
                                    }
                                },
                        ),
                adapter = rememberScrollbarAdapter(scrollState),
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
    }
}
