package com.crosspaste.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.search
import com.crosspaste.ui.base.settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeView(currentPageViewContext: MutableState<PageViewContext>) {
    HomeWindowDecoration()
    TabsView(currentPageViewContext)
}

val customFontFamily =
    FontFamily(
        Font(resource = "font/BebasNeue.otf", FontWeight.Normal),
    )

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Preview
@Composable
fun HomeWindowDecoration() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appWindowManager = current.koin.get<AppWindowManager>()
    val pasteSearchService = current.koin.get<PasteSearchService>()

    val scope = rememberCoroutineScope()

    var showPopup by remember { mutableStateOf(false) }

    var hoverSearchIcon by remember { mutableStateOf(false) }

    var hoverSettingsIcon by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    Box(
        modifier =
            Modifier.background(Color.Transparent),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    modifier =
                        Modifier.padding(start = 13.dp, top = 13.dp, end = 10.dp, bottom = 13.dp)
                            .align(Alignment.CenterVertically)
                            .clip(RoundedCornerShape(3.dp))
                            .size(36.dp),
                    painter = painterResource("crosspaste_icon.png"),
                    contentDescription = "crosspaste icon",
                )
                Column(
                    Modifier.wrapContentWidth()
                        .height(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        modifier = Modifier.align(Alignment.Start),
                        text = "Compile Future",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp,
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                            ),
                    )
                    Text(
                        modifier = Modifier.align(Alignment.Start),
                        text = "CrossPaste",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 25.sp,
                        style =
                            TextStyle(
                                fontFamily = customFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                Modifier.wrapContentSize(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PasteTooltipAreaView(
                    modifier = Modifier.size(32.dp),
                    text = copywriter.getText("open_search_window"),
                ) {
                    Box(
                        modifier =
                            Modifier.size(32.dp)
                                .onPointerEvent(
                                    eventType = PointerEventType.Enter,
                                    onEvent = {
                                        hoverSearchIcon = true
                                    },
                                )
                                .onPointerEvent(
                                    eventType = PointerEventType.Exit,
                                    onEvent = {
                                        hoverSearchIcon = false
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (hoverSearchIcon) {
                                            MaterialTheme.colors.surface.copy(0.64f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).onClick {
                                        scope.launch {
                                            appWindowManager.unActiveMainWindow()
                                            delay(100)
                                            pasteSearchService.activeWindow()
                                        }
                                    },
                        ) {}

                        Icon(
                            painter = search(),
                            contentDescription = "open search window",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colors.onBackground,
                        )
                    }
                }

                PasteTooltipAreaView(
                    modifier = Modifier.size(32.dp),
                    text = "CrossPaste ${copywriter.getText("menu")}",
                ) {
                    Box(
                        modifier =
                            Modifier.size(32.dp)
                                .onPointerEvent(
                                    eventType = PointerEventType.Enter,
                                    onEvent = {
                                        hoverSettingsIcon = true
                                    },
                                )
                                .onPointerEvent(
                                    eventType = PointerEventType.Exit,
                                    onEvent = {
                                        hoverSettingsIcon = false
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (hoverSettingsIcon) {
                                            MaterialTheme.colors.surface.copy(0.64f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).onClick {
                                        showPopup = !showPopup
                                    },
                        ) {}

                        Icon(
                            painter = settings(),
                            contentDescription = "info",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colors.onBackground,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(15.dp))

                if (showPopup) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset =
                            IntOffset(
                                with(density) { ((-14).dp).roundToPx() },
                                with(density) { (30.dp).roundToPx() },
                            ),
                        onDismissRequest = {
                            if (showPopup) {
                                showPopup = false
                            }
                        },
                        properties =
                            PopupProperties(
                                focusable = true,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true,
                            ),
                    ) {
                        HomeMenuView {
                            showPopup = false
                        }
                    }
                }
            }
        }
    }
}
