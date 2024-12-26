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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppUrls
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.base.robotoFontFamily
import org.koin.compose.koinInject

@Composable
fun AboutContentView() {
    val appInfo = koinInject<AppInfo>()
    val appUrls = koinInject<AppUrls>()
    val uiSupport = koinInject<UISupport>()

    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.align(Alignment.Center)
                    .offset(y = (-30).dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CrossPasteLogooView(
                    modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .size(72.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "CrossPaste",
                    style =
                        TextStyle(
                            fontFamily = robotoFontFamily(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "version: ${appInfo.displayVersion()}",
                    style =
                        TextStyle(
                            fontFamily = robotoFontFamily(),
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(30.dp))

                AboutInfoItem("official_website") {
                    uiSupport.openCrossPasteWebInBrowser()
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 80.dp),
                    thickness = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                )

                AboutInfoItem("newbie_tutorial") {
                    uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard")
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 80.dp),
                    thickness = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                )

                AboutInfoItem("change_log") {
                    uiSupport.openUrlInBrowser(appUrls.changeLogUrl)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 80.dp),
                    thickness = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                )

                AboutInfoItem("feedback") {
                    uiSupport.openUrlInBrowser(appUrls.issueTrackerUrl)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 80.dp),
                    thickness = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
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
            Modifier.fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 80.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.wrapContentSize().padding(start = 5.dp),
            text = copywriter.getText(title),
            style = MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            painter = chevronRight(),
            contentDescription = "chevron right",
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
