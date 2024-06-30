package com.clipevery.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppInfo
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.UISupport
import com.clipevery.ui.base.chevronRight

@Composable
fun AboutView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "About")
    AboutContentView()
}

@Composable
fun AboutContentView() {
    val current = LocalKoinApplication.current
    val appInfo = current.koin.get<AppInfo>()
    val uiSupport = current.koin.get<UISupport>()

    Box(
        modifier = Modifier.fillMaxSize(),
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
                Image(
                    modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .size(72.dp),
                    painter = painterResource("clipevery_icon.png"),
                    contentDescription = "clipevery icon",
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Clipevery",
                    style =
                        TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                    color = MaterialTheme.colors.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "version: ${appInfo.appVersion}",
                    style =
                        TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                        ),
                    color = MaterialTheme.colors.onBackground,
                )
                Spacer(modifier = Modifier.height(30.dp))

                AboutInfoItem("Official_website") {
                    uiSupport.openUrlInBrowser("https://clipevery.com")
                }

                Divider(modifier = Modifier.padding(horizontal = 80.dp))

                AboutInfoItem("Change_log") {
                    uiSupport.openUrlInBrowser("https://github.com/clipevery/clipevery-desktop/blob/main/CHANGELOG.md")
                }

                Divider(modifier = Modifier.padding(horizontal = 80.dp))

                AboutInfoItem("Feedback") {
                    uiSupport.openUrlInBrowser("https://github.com/clipevery/clipevery-desktop/issues")
                }

                Divider(modifier = Modifier.padding(horizontal = 80.dp))

                AboutInfoItem("Contact_us") {
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
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 80.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onClick()
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.wrapContentSize().padding(start = 5.dp),
            text = copywriter.getText(title),
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground,
            fontSize = 15.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            painter = chevronRight(),
            contentDescription = "chevron right",
            tint = MaterialTheme.colors.onBackground,
        )
    }
}
