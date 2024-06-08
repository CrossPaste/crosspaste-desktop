package com.clipevery.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.clipevery.LocalExitApplication
import com.clipevery.LocalKoinApplication
import com.clipevery.LocalPageViewContent
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.UISupport
import com.clipevery.ui.base.getMenWidth

@Composable
fun MenuView(
    openMainWindow: () -> Unit = {},
    close: () -> Unit,
) {
    val current = LocalKoinApplication.current
    val currentPage = LocalPageViewContent.current
    val applicationExit = LocalExitApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val uiSupport = current.koin.get<UISupport>()

    Box(
        modifier =
            Modifier
                .wrapContentSize()
                .background(Color.Transparent)
                .shadow(15.dp),
    ) {
        val menuTexts =
            arrayOf(
                copywriter.getText("Check_for_updates"),
                copywriter.getText("Settings"),
                copywriter.getText("ShortcutKeys"),
                copywriter.getText("About"),
                copywriter.getText("FQA"),
                copywriter.getText("Quit"),
            )

        val maxWidth = max(150.dp, getMenWidth(menuTexts))

        Column(
            modifier =
                Modifier
                    .width(maxWidth)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colors.surface),
        ) {
            MenuItem(copywriter.getText("Check_for_updates")) {
                // TODO: check for updates
                close()
            }
            MenuItem(copywriter.getText("Settings")) {
                openMainWindow()
                currentPage.value = PageViewContext(PageViewType.SETTINGS, currentPage.value)
                close()
            }
            MenuItem(copywriter.getText("Shortcut_Keys")) {
                openMainWindow()
                currentPage.value = PageViewContext(PageViewType.SHORTCUT_KEYS, currentPage.value)
                close()
            }
            MenuItem(copywriter.getText("About")) {
                openMainWindow()
                currentPage.value = PageViewContext(PageViewType.ABOUT, currentPage.value)
                close()
            }
            MenuItem(copywriter.getText("FQA")) {
                uiSupport.openUrlInBrowser("https://www.clipevery.com/FQA")
                close()
            }
            Divider()
            MenuItem(copywriter.getText("Quit")) {
                close()
                applicationExit()
            }
        }
    }
}
