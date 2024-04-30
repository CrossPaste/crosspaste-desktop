package com.clipevery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.ui.ClipeveryTheme
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun createSearchWindow(
    clipSearchService: ClipSearchService,
    koinApplication: KoinApplication,
    coroutineScope: CoroutineScope,
) {
    val appWindowManager = clipSearchService.appWindowManager
    if (clipSearchService.tryStart()) {
        application {
            val windowState =
                rememberWindowState(
                    placement = WindowPlacement.Floating,
                    position = WindowPosition.Aligned(Alignment.Center),
                    size = appWindowManager.searchWindowDpSize,
                )

            Window(
                onCloseRequest = ::exitApplication,
                visible = appWindowManager.showSearchWindow,
                state = windowState,
                title = appWindowManager.searchWindowTitle,
                alwaysOnTop = true,
                undecorated = true,
                transparent = true,
                resizable = false,
            ) {
                DisposableEffect(Unit) {
                    val windowListener =
                        object : WindowAdapter() {
                            override fun windowGainedFocus(e: WindowEvent?) {
                                appWindowManager.showSearchWindow = true
                            }

                            override fun windowLostFocus(e: WindowEvent?) {
                                appWindowManager.showSearchWindow = false
                            }
                        }

                    window.addWindowFocusListener(windowListener)

                    onDispose {
                        window.removeWindowFocusListener(windowListener)
                    }
                }

                ClipeveryAppSearchView(
                    koinApplication,
                    hideWindow = { appWindowManager.showSearchWindow = false },
                )
            }
        }
    } else {
        coroutineScope.launch(CoroutineName("ActiveSearchWindow")) {
            clipSearchService.activeWindow()
        }
    }
}

@Composable
fun ClipeveryAppSearchView(
    koinApplication: KoinApplication,
    hideWindow: () -> Unit,
) {
    CompositionLocalProvider(
        LocalKoinApplication provides koinApplication,
    ) {
        ClipeverySearchWindow(hideWindow)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClipeverySearchWindow(hideWindow: () -> Unit) {
    val current = LocalKoinApplication.current
    val inputModeManager = LocalInputModeManager.current
    val clipSearchService = current.koin.get<ClipSearchService>()
    val appWindowManager = clipSearchService.appWindowManager
    val logger = current.koin.get<KLogger>()

    var lastInputTime by remember { mutableStateOf(0L) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(appWindowManager.showSearchWindow) {
        if (appWindowManager.showSearchWindow) {
            delay(200)
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    LaunchedEffect(clipSearchService.inputSearch.value) {
        val currentTime = System.currentTimeMillis()
        lastInputTime = currentTime
        if (clipSearchService.inputSearch.value.trim().isNotEmpty()) {
            delay(500)
        }
        if (lastInputTime == currentTime) {
            clipSearchService.search()
        }
    }

    ClipeveryTheme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .clip(RoundedCornerShape(10.dp))
                    .width(800.dp)
                    .height(480.dp)
                    .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(5.dp, RoundedCornerShape(10.dp))
                        .width(780.dp)
                        .height(460.dp)
                        .background(MaterialTheme.colors.background)
                        .onPreviewKeyEvent {
                            it.key == Key(KeyEvent.VK_UP) || it.key == Key(KeyEvent.VK_DOWN)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    Row(modifier = Modifier.height(60.dp).fillMaxWidth()) {
                        TextField(
                            modifier =
                                Modifier.focusTarget()
                                    .focusRequester(focusRequester)
                                    .onFocusEvent {
                                        logger.debug { "onFocusEvent $it" }
                                    }
                                    .onFocusChanged {
                                        logger.debug { "onFocusChanged $it" }
                                    }
                                    .fillMaxSize(),
                            value = clipSearchService.inputSearch.value,
                            onValueChange = { clipSearchService.updateInputSearch(it) },
                            keyboardOptions = KeyboardOptions.Default.copy(autoCorrect = true),
                            visualTransformation = VisualTransformation.None,
                            placeholder = {
                                Text(
                                    modifier = Modifier.wrapContentSize(),
                                    text = "please input search",
                                    style =
                                        TextStyle(
                                            fontWeight = FontWeight.Light,
                                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                                            fontSize = 15.sp,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            isError = false,
                            singleLine = true,
                            colors =
                                TextFieldDefaults.textFieldColors(
                                    textColor = MaterialTheme.colors.onBackground,
                                    disabledTextColor = Color.Transparent,
                                    backgroundColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colors.primary,
                                    errorCursorColor = Color.Red,
                                    focusedIndicatorColor = MaterialTheme.colors.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colors.secondaryVariant,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            textStyle =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 15.sp,
                                    lineHeight = 5.sp,
                                ),
                        )
                    }
                    Box {
                        SearchResultView()
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultView() {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()

    Row(modifier = Modifier.fillMaxSize()) {
        SearchListView {
            clipSearchService.clickSetSelectedIndex(it)
        }
        Divider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            thickness = 2.dp,
        )

        DetialClipDataView()
    }
}
