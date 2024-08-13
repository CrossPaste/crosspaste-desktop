package com.crosspaste.ui.devices

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppTokenService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.Fonts.ROBOTO_FONT_FAMILY

@Composable
fun TokenView() {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appTokenService = current.koin.get<AppTokenService>()

    val offsetY =
        animateIntOffsetAsState(
            targetValue =
                if (appTokenService.showToken) {
                    IntOffset(
                        with(density) { (0.dp).roundToPx() },
                        with(density) { (50.dp).roundToPx() },
                    )
                } else {
                    IntOffset(
                        with(density) { (0.dp).roundToPx() },
                        with(density) { ((-100).dp).roundToPx() },
                    )
                },
            animationSpec = tween(durationMillis = 500),
        )

    val alpha by animateFloatAsState(
        targetValue = if (appTokenService.showToken) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = 500,
                easing = LinearOutSlowInEasing,
            ),
    )

    LaunchedEffect(appTokenService.showToken) {
        appTokenService.startRefreshToken()
    }

    DisposableEffect(appTokenService.showToken) {
        onDispose {
            appTokenService.stopRefreshToken()
        }
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = offsetY.value,
        properties = PopupProperties(clippingEnabled = false),
    ) {
        Box(
            modifier =
                Modifier
                    .alpha(alpha)
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
                        .background(MaterialTheme.colors.surface),
            ) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .width(320.dp),
                ) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = copywriter.getText("token"),
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ROBOTO_FONT_FAMILY,
                    )
                    IconButton(
                        onClick = { appTokenService.showToken = false },
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .offset(y = (-8).dp)
                                .padding(0.dp)
                                .size(16.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .padding(0.dp),
                        )
                    }
                }
                Row(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(12.dp),
                ) {
                    OTPCodeBox(appTokenService)
                }
            }
        }
    }
}

@Composable
fun OTPCodeBox(appTokenService: AppTokenService) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            appTokenService.token.forEach { char ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colors.background, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                ) {
                    Text(
                        text = char.toString(),
                        color = MaterialTheme.colors.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.width(300.dp).wrapContentHeight()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(5.dp),
                progress = appTokenService.showTokenProgress,
            )
        }
    }
}
