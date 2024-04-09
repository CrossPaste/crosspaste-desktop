package com.clipevery.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.chevronRight

@Composable
fun AboutView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "About")
    AboutContentView()
}

@Composable
fun AboutContentView() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

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
                        Modifier.clip(RoundedCornerShape(3.dp))
                            .size(72.dp),
                    painter = painterResource("clipevery_icon.png"),
                    contentDescription = "clipevery icon",
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ClipEvery",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Version 1.0.0", // todo get version
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(30.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 80.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = copywriter.getText("Official_website"),
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

                Divider(modifier = Modifier.padding(horizontal = 80.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 80.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = copywriter.getText("Change_log"),
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

                Divider(modifier = Modifier.padding(horizontal = 80.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 80.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = copywriter.getText("Contact_us"),
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
        }
    }

}
