package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.crosspaste.LocalExitApplication
import com.crosspaste.LocalKoinApplication
import com.crosspaste.LocalPageViewContent
import com.crosspaste.app.AppUpdateService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.getMenWidth

@Composable
fun HomeMenuView(
    openMainWindow: () -> Unit = {},
    close: () -> Unit,
) {
    val current = LocalKoinApplication.current
    val currentPage = LocalPageViewContent.current
    val applicationExit = LocalExitApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val uiSupport = current.koin.get<UISupport>()
    val appUpdateService = current.koin.get<AppUpdateService>()

    val existNewVersion by remember { mutableStateOf(appUpdateService.existNewVersion()) }

    Box(
        modifier =
            Modifier
                .wrapContentSize()
                .background(Color.Transparent)
                .shadow(15.dp),
    ) {
        val menuTexts =
            arrayOf(
                copywriter.getText("check_for_updates"),
                copywriter.getText("settings"),
                copywriter.getText("shortcut_keys"),
                copywriter.getText("about"),
                copywriter.getText("fqa"),
                copywriter.getText("quit"),
            )

        val maxWidth =
            max(
                150.dp,
                getMenWidth(menuTexts, extendFunction = {
                    if (existNewVersion && it == 0) {
                        24.dp
                    } else {
                        0.dp
                    }
                }),
            )

        Column(
            modifier =
                Modifier
                    .width(maxWidth)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colors.surface),
        ) {
            MenuItem(copywriter.getText("check_for_updates"), reminder = existNewVersion) {
                appUpdateService.jumpDownload()
                close()
            }
            MenuItem(copywriter.getText("settings")) {
                openMainWindow()
                currentPage.value = PageViewContext(PageViewType.SETTINGS, currentPage.value)
                close()
            }
            MenuItem(copywriter.getText("shortcut_keys")) {
                openMainWindow()
                currentPage.value = PageViewContext(PageViewType.SHORTCUT_KEYS, currentPage.value)
                close()
            }
            MenuItem(copywriter.getText("about")) {
                openMainWindow()
                currentPage.value = PageViewContext(PageViewType.ABOUT, currentPage.value)
                close()
            }
            MenuItem(copywriter.getText("fqa")) {
                uiSupport.openUrlInBrowser("https://www.crosspaste.com/FQA")
                close()
            }
            Divider()
            MenuItem(copywriter.getText("quit")) {
                close()
                applicationExit()
            }
        }
    }
}
