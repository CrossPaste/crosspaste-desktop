package com.crosspaste.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppTokenApi
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.ui.base.BaseColor
import com.crosspaste.ui.base.autoRenew
import com.crosspaste.ui.base.scan
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.qrTextStyle
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.ColorUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun QRContentView() {
    val appTokenApi = koinInject<AppTokenApi>()
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val qrCodeGenerator = koinInject<QRCodeGenerator>()

    val density = LocalDensity.current

    val width = with(density) { appSize.qrCodeSize.width.roundToPx() }
    val height = with(density) { appSize.qrCodeSize.height.roundToPx() }

    var qrImage: ImageBitmap? by remember { mutableStateOf(null) }

    val token by appTokenApi.token.collectAsState()

    LaunchedEffect(token) {
        // maybe slow (get host), we use ioDispatcher to avoid blocking the UI
        qrImage =
            withContext(ioDispatcher) {
                qrCodeGenerator.generateQRCode(token)
                    .toImage(width, height) as ImageBitmap
            }
    }

    DisposableEffect(Unit) {
        appTokenApi.startRefreshToken()
        onDispose {
            appTokenApi.stopRefreshToken()
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(tinyRoundedCornerShape)
                .background(AppUIColors.generalBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -xxxxLarge),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .width(appSize.qrCodeSize.width)
                            .clip(small3XRoundedCornerShape)
                            .background(Color.White)
                            .padding(horizontal = medium, vertical = tiny2X),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier =
                            Modifier.weight(1f, fill = false)
                                .padding(vertical = tiny),
                        text = copywriter.getText("please_scan_the_binding_device"),
                        maxLines = 3,
                        color = Color.Black,
                        softWrap = true,
                        style = qrTextStyle,
                    )
                    Spacer(modifier = Modifier.width(tiny))
                    Icon(
                        painter = scan(),
                        contentDescription = "Scan",
                        modifier = Modifier.size(large2X),
                        tint = ColorUtils.getAdaptiveColor(Color.White, BaseColor.Blue.targetHue),
                    )
                }
                Spacer(modifier = Modifier.height(large2X))
                qrImage?.let {
                    Image(
                        modifier =
                            Modifier.size(appSize.qrCodeSize)
                                .clip(small3XRoundedCornerShape),
                        bitmap = it,
                        contentDescription = "QR Code",
                    )
                } ?: run {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                    )

                    Box(
                        modifier = Modifier.size(appSize.qrCodeSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            modifier =
                                Modifier.size(appSize.qrCodeSize / 2)
                                    .graphicsLayer(rotationZ = rotation),
                            painter = autoRenew(),
                            contentDescription = "QR Code",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}
