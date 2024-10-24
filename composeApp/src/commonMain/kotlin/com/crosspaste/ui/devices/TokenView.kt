package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppTokenService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.close
import com.crosspaste.ui.base.robotoFontFamily
import org.koin.compose.koinInject

@Composable
fun TokenView() {
    val density = LocalDensity.current
    val copywriter = koinInject<GlobalCopywriter>()
    val appTokenService = koinInject<AppTokenService>()

    val offsetY =
        IntOffset(
            with(density) { (0.dp).roundToPx() },
            with(density) { (50.dp).roundToPx() },
        )

    DisposableEffect(Unit) {
        appTokenService.startRefreshToken()

        onDispose {
            appTokenService.stopRefreshToken()
        }
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = offsetY,
        properties = PopupProperties(clippingEnabled = false),
    ) {
        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .background(Color.Transparent)
                    .shadow(15.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .wrapContentSize()
                        .clip(RoundedCornerShape(5.dp))
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.surface),
            ) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(8.dp)
                            .width(320.dp),
                ) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = copywriter.getText("token"),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = robotoFontFamily(),
                    )

                    Box(
                        modifier =
                            Modifier.size(32.dp)
                                .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        appTokenService.toHideToken()
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = close(),
                                contentDescription = "Close",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
                Row(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    OTPCodeBox(appTokenService)
                }
            }
        }
    }
}

@Composable
fun OTPCodeBox(appTokenService: AppTokenService) {
    val token by appTokenService.token.collectAsState()

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            token.forEach { char ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                ) {
                    Text(
                        text = char.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.width(300.dp).wrapContentHeight()) {
            val progress by appTokenService.showTokenProgression.collectAsState()
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(1.5.dp)),
                progress = { progress },
            )
        }
    }
}
