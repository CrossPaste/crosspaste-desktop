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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.TutorialButton
import com.crosspaste.ui.base.search
import com.crosspaste.ui.base.settings
import com.crosspaste.ui.base.share
import com.crosspaste.ui.theme.AppUIFont.appNameTextStyle
import com.crosspaste.ui.theme.AppUIFont.companyTextStyle
import com.crosspaste.ui.theme.AppUISize.massive
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Preview
@Composable
fun HomeWindowDecoration() {
    val appLaunchState = koinInject<AppLaunchState>()
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
                    .wrapContentHeight()
                    .padding(small),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrossPasteLogoView(
                    size = xxxLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(small3X))
                Column(
                    Modifier.wrapContentWidth()
                        .height(xxxLarge),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Compile Future",
                        style = companyTextStyle,
                    )
                    Box {
                        Text(
                            text = "CrossPaste",
                            style = appNameTextStyle,
                        )
                        if (existNewVersion) {
                            NewVersionButton(modifier = Modifier.offset(x = massive, y = -small3X))
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
                Spacer(modifier = Modifier.width(tiny3X))

                PasteTooltipIconView(
                    painter = share(),
                    text = copywriter.getText("recommend"),
                    contentDescription = "recommend",
                ) {
                    appWindowManager.toScreen(Recommend)
                }

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

                if (showPopup) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset =
                            IntOffset(
                                with(density) { (-small).roundToPx() },
                                with(density) { (xxLarge).roundToPx() },
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
