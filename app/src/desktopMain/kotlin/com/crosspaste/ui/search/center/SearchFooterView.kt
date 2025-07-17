package com.crosspaste.ui.search.center

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.search.QuickPasteView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import org.koin.compose.koinInject

@Composable
fun SearchFooterView() {
    val appInfo = koinInject<AppInfo>()
    val appSize = koinInject<DesktopAppSize>()
    val appUpdateService = koinInject<AppUpdateService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val existNewVersion by appUpdateService.existNewVersion().collectAsState(false)

    val prevAppName by appWindowManager.getPrevAppName().collectAsState(null)

    Row(
        modifier =
            Modifier
                .height(appSize.centerSearchFooterHeight)
                .fillMaxWidth()
                .background(AppUIColors.generalBackground)
                .padding(horizontal = small3X),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CrossPasteLogoView(
            size = xLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.width(small3X))

        Text(
            text = "CrossPaste ${appInfo.appVersion}",
            style =
                AppUIFont.mediumBodyTextStyle.copy(
                    color =
                        MaterialTheme.colorScheme.contentColorFor(
                            AppUIColors.generalBackground,
                        ),
                ),
        )

        if (existNewVersion) {
            Spacer(modifier = Modifier.width(small3X))
            NewVersionButton()
        }

        Spacer(modifier = Modifier.weight(1f))

        prevAppName?.let {
            Text(
                text = "${copywriter.getText("paste_to")} $it",
                style =
                    AppUIFont.tipsTextStyle.copy(
                        color =
                            MaterialTheme.colorScheme.contentColorFor(
                                AppUIColors.generalBackground,
                            ),
                    ),
            )
            Spacer(modifier = Modifier.width(tiny))
            QuickPasteView()
        }
    }
}
