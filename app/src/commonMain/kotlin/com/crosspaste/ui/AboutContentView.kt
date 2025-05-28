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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppUrls
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CrossPasteLogoView
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.base.robotoFontFamily
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.medium
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
            Modifier.fillMaxSize()
                .padding(medium)
                .clip(tinyRoundedCornerShape)
                .background(AppUIColors.aboutBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.align(Alignment.Center)
                    .offset(y = -xxLarge),
        ) {
            val onBackground = MaterialTheme.colorScheme.contentColorFor(AppUIColors.aboutBackground)

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
                    style =
                        TextStyle(
                            fontFamily = robotoFontFamily(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                    color = onBackground,
                )
                Spacer(modifier = Modifier.height(small2X))

                Text(
                    text = "version: ${appInfo.displayVersion()}",
                    style =
                        TextStyle(
                            fontFamily = robotoFontFamily(),
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                        ),
                    color = onBackground,
                )
                Spacer(modifier = Modifier.height(xxLarge))

                AboutInfoItem("official_website", onBackground) {
                    uiSupport.openCrossPasteWebInBrowser()
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("newbie_tutorial", onBackground) {
                    uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard")
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("change_log", onBackground) {
                    uiSupport.openUrlInBrowser(appUrls.changeLogUrl)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("feedback", onBackground) {
                    uiSupport.openUrlInBrowser(appUrls.issueTrackerUrl)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = giant),
                    thickness = tiny5X,
                    color = onBackground.copy(alpha = 0.36f),
                )

                AboutInfoItem("contact_us", onBackground) {
                    uiSupport.openEmailClient("compile.future@gmail.com")
                }
            }
        }
    }
}

@Composable
fun AboutInfoItem(
    title: String,
    onBackground: Color,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(xxxxLarge)
                .padding(horizontal = giant, vertical = tiny2X)
                .clip(tinyRoundedCornerShape)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier =
                Modifier.wrapContentSize()
                    .padding(start = tiny2X),
            text = copywriter.getText(title),
            color = onBackground,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            painter = chevronRight(),
            contentDescription = "chevron right",
            tint = onBackground,
        )
    }
}
