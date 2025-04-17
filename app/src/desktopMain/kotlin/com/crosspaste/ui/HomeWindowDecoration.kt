package com.crosspaste.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.TutorialButton
import com.crosspaste.ui.base.robotoFontFamily
import com.crosspaste.ui.base.search
import com.crosspaste.ui.base.settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Preview
@Composable
fun HomeWindowDecoration() {
    val appLaunchState = koinInject<DesktopAppLaunchState>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val appUpdateService = koinInject<AppUpdateService>()
    val configManager = koinInject<ConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val scope = rememberCoroutineScope()

    var showPopup by remember { mutableStateOf(false) }

    val config by configManager.config.collectAsState()

    val density = LocalDensity.current

    val existNewVersion by appUpdateService.existNewVersion().collectAsState(false)

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
                CrossPasteLogoView(
                    modifier =
                        Modifier.padding(start = 13.dp, top = 13.dp, end = 10.dp, bottom = 13.dp)
                            .align(Alignment.CenterVertically)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .size(36.dp),
                )
                Column(
                    Modifier.wrapContentWidth()
                        .height(36.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Compile Future",
                        color = MaterialTheme.colorScheme.onSurface,
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                                fontSize = 10.sp,
                            ),
                    )
                    Box {
                        Text(
                            text = "CrossPaste",
                            color = MaterialTheme.colorScheme.onSurface,
                            style =
                                TextStyle(
                                    fontFamily = robotoFontFamily(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 23.sp,
                                ),
                        )
                        if (existNewVersion) {
                            NewVersionButton(modifier = Modifier.offset(x = 95.dp, y = (-10).dp))
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
                if (appLaunchState.firstLaunch && config.showTutorial) {
                    TutorialButton()
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
                        appWindowManager.activeSearchWindow()
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
