package com.crosspaste.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopPasteSearchService
import com.crosspaste.ui.base.Fonts.ROBOTO_FONT_FAMILY
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.menuItemReminderTextStyle
import com.crosspaste.ui.base.search
import com.crosspaste.ui.base.settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun HomeView(currentPageViewContext: MutableState<PageViewContext>) {
    HomeWindowDecoration()
    TabsView(currentPageViewContext)
}

@Preview
@Composable
fun HomeWindowDecoration() {
    val copywriter = koinInject<GlobalCopywriter>()
    val appLaunchState = koinInject<AppLaunchState>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val appUpdateService = koinInject<AppUpdateService>()
    val pasteSearchService = koinInject<DesktopPasteSearchService>()
    val configManager = koinInject<ConfigManager>()
    val uiSupport = koinInject<UISupport>()

    val scope = rememberCoroutineScope()

    var showPopup by remember { mutableStateOf(false) }

    var showTutorial by remember { mutableStateOf(configManager.config.showTutorial) }

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
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Compile Future",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp,
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                            ),
                    )
                    Box {
                        Text(
                            text = "CrossPaste",
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 23.sp,
                            style =
                                TextStyle(
                                    fontFamily = ROBOTO_FONT_FAMILY,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        if (appUpdateService.existNewVersion()) {
                            Row(
                                modifier =
                                    Modifier
                                        .offset(x = 95.dp, y = (-5).dp)
                                        .width(32.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                        .clickable {
                                            appUpdateService.jumpDownload()
                                        },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "new!",
                                    color = Color.White,
                                    style = menuItemReminderTextStyle,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                Modifier.wrapContentSize(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (appLaunchState.firstLaunch && showTutorial) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.95f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                    )

                    Row(
                        modifier =
                            Modifier
                                .wrapContentWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colors.primary)
                                .clickable {
                                    uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard")
                                    configManager.updateConfig("showTutorial", false)
                                    showTutorial = configManager.config.showTutorial
                                },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier =
                                Modifier.padding(horizontal = 6.dp)
                                    .scale(scale),
                            text = copywriter.getText("newbie_tutorial"),
                            color = Color.White,
                            fontSize = 12.sp,
                            style = menuItemReminderTextStyle,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(5.dp))

                PasteTooltipIconView(
                    painter = search(),
                    text = copywriter.getText("open_search_window"),
                    contentDescription = "open search window",
                ) {
                    scope.launch {
                        appWindowManager.unActiveMainWindow()
                        delay(100)
                        pasteSearchService.activeWindow()
                    }
                }

                PasteTooltipIconView(
                    painter = settings(),
                    text = "CrossPaste ${copywriter.getText("menu")}",
                    contentDescription = "settings",
                ) {
                    showPopup = !showPopup
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
