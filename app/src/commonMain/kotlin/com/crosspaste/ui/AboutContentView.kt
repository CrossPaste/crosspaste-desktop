package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppUrls
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.aboutAppNameTextStyle
import com.crosspaste.ui.theme.AppUIFont.aboutInfoTextStyle
import com.crosspaste.ui.theme.AppUIFont.aboutVersionTextStyle
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun AboutContentView() {
    val appInfo = koinInject<AppInfo>()
    val appUrls = koinInject<AppUrls>()
    val uiSupport = koinInject<UISupport>()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(tinyRoundedCornerShape)
                .background(AppUIColors.generalBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -xxLarge),
        ) {
            val onBackground =
                MaterialTheme.colorScheme.contentColorFor(
                    AppUIColors.generalBackground,
                )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CrossPasteLogoView(
                    size = giant,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(small))
                Text(
                    text = "CrossPaste",
                    style = aboutAppNameTextStyle,
                )
                Spacer(modifier = Modifier.height(small2X))

                Text(
                    text = "version: ${appInfo.displayVersion()}",
                    style = aboutVersionTextStyle,
                )
                Spacer(modifier = Modifier.height(xxLarge))

                AboutInfoItem("official_website") {
                    uiSupport.openCrossPasteWebInBrowser()
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("newbie_tutorial") {
                    uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard")
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("change_log") {
                    uiSupport.openUrlInBrowser(appUrls.changeLogUrl)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("feedback") {
                    uiSupport.openUrlInBrowser(appUrls.issueTrackerUrl)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("contact_us") {
                    uiSupport.openEmailClient("compile.future@gmail.com")
                }
            }
        }
    }
}

@Composable
fun AboutInfoItem(
    title: String,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(xxxxLarge)
                .padding(horizontal = giant, vertical = tiny2X)
                .clip(tinyRoundedCornerShape)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier =
                Modifier
                    .wrapContentSize()
                    .padding(start = tiny2X),
            text = copywriter.getText(title),
            style = aboutInfoTextStyle,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            painter = chevronRight(),
            contentDescription = "chevron right",
            tint =
                MaterialTheme.colorScheme.contentColorFor(
                    AppUIColors.generalBackground,
                ),
        )
    }
}
