package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun HomeMenuView(
    openMainWindow: () -> Unit = {},
    close: () -> Unit,
) {
    val applicationExit = LocalExitApplication.current
    val appWindowManager = koinInject<AppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val appUpdateService = koinInject<AppUpdateService>()

    val existNewVersion by appUpdateService.existNewVersion().collectAsState(false)

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
                copywriter.getText("export"),
                copywriter.getText("import"),
                copywriter.getText("about"),
                copywriter.getText("faq"),
                copywriter.getText("quit"),
            )

        val newWidth =
            measureTextWidth(
                "new!",
                MaterialTheme.typography.bodySmall
                    .copy(fontStyle = FontStyle.Italic),
            )

        val maxWidth =
            getMenWidth(menuTexts, extendFunction = {
                if (existNewVersion && it == 0) {
                    medium + newWidth
                } else {
                    zero
                }
            })

        Column(
            modifier =
                Modifier
                    .width(maxWidth)
                    .wrapContentHeight()
                    .clip(tiny2XRoundedCornerShape)
                    .background(MaterialTheme.colorScheme.surfaceBright),
        ) {
            MenuItem(
                text = copywriter.getText("settings"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                openMainWindow()
                appWindowManager.toScreen(Settings)
                close()
            }
            MenuItem(
                text = copywriter.getText("shortcut_keys"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                openMainWindow()
                appWindowManager.toScreen(ShortcutKeys)
                close()
            }
            MenuItem(
                text = copywriter.getText("check_for_updates"),
                background = MaterialTheme.colorScheme.surfaceBright,
                extendContent =
                    if (existNewVersion) {
                        {
                            Spacer(modifier = Modifier.width(tiny))
                            NewVersionButton()
                        }
                    } else {
                        null
                    },
            ) {
                appUpdateService.jumpDownload()
                close()
            }
            MenuItem(
                text = copywriter.getText("export"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                openMainWindow()
                appWindowManager.toScreen(Export)
                close()
            }
            MenuItem(
                text = copywriter.getText("import"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                openMainWindow()
                appWindowManager.toScreen(Import)
                close()
            }
            MenuItem(
                text = copywriter.getText("about"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                openMainWindow()
                appWindowManager.toScreen(About)
                close()
            }
            MenuItem(
                text = copywriter.getText("faq"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                uiSupport.openCrossPasteWebInBrowser(path = "FAQ")
                close()
            }
            HorizontalDivider()
            MenuItem(
                text = copywriter.getText("quit"),
                background = MaterialTheme.colorScheme.surfaceBright,
            ) {
                close()
                applicationExit(ExitMode.EXIT)
            }
        }
    }
}
