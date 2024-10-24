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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppTokenService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.ui.base.autoRenew
import com.crosspaste.ui.base.scan
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
actual fun QRScreen() {
    val copywriter = koinInject<GlobalCopywriter>()
    val appTokenService = koinInject<AppTokenService>()
    val qrCodeGenerator = koinInject<QRCodeGenerator>()

    val density = LocalDensity.current

    val width = with(density) { qrCodeGenerator.qrSize.width.roundToPx() }
    val height = with(density) { qrCodeGenerator.qrSize.height.roundToPx() }

    var qrImage: ImageBitmap? by remember { mutableStateOf(null) }

    val token by appTokenService.token.collectAsState()

    LaunchedEffect(Unit) {
        appTokenService.startRefreshToken()
    }

    LaunchedEffect(appTokenService.token) {
        // maybe slow (get host), we use ioDispatcher to avoid blocking the UI
        qrImage =
            withContext(ioDispatcher) {
                qrCodeGenerator.generateQRCode(width, height, token)
                    .toImage() as ImageBitmap
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            appTokenService.stopRefreshToken()
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface.copy(0.64f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-48).dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .width(275.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        modifier =
                            Modifier.weight(1f, fill = false)
                                .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        text = copywriter.getText("please_scan_the_binding_device"),
                        maxLines = 3,
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 20.sp,
                                lineHeight = 24.sp,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = scan(),
                        contentDescription = "Scan",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                qrImage?.let {
                    Image(
                        modifier =
                            Modifier.size(qrCodeGenerator.qrSize.width)
                                .clip(RoundedCornerShape(10.dp)),
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
                        modifier = Modifier.size(qrCodeGenerator.qrSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            modifier =
                                Modifier.size(100.dp)
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
