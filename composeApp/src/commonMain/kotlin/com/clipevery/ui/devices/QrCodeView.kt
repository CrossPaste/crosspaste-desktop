package com.clipevery.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppUI
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.autoRenew
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.ioDispatcher
import compose.icons.TablerIcons
import compose.icons.tablericons.Scan
import kotlinx.coroutines.withContext


val qrSize: DpSize = DpSize(275.dp, 275.dp)

@Composable
fun bindingQRCode() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appUI = current.koin.get<AppUI>()
    val qrCodeGenerator = current.koin.get<QRCodeGenerator>()

    val density = LocalDensity.current

    val width = with(density) { qrSize.width.roundToPx() }
    val height = with(density) { qrSize.height.roundToPx() }

    var qrImage: ImageBitmap? by remember { mutableStateOf(null)}

    LaunchedEffect(Unit) {
        appUI.startRefreshToken()
    }

    LaunchedEffect(appUI.token) {
        // maybe slow (get host), we use ioDispatcher to avoid blocking the UI
        qrImage = withContext(ioDispatcher) {
            qrCodeGenerator.generateQRCode(width, height, appUI.token)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            appUI.stopRefreshToken()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // This Box will be the 2:1 rectangle with the QR code and close button
        Box(
            modifier = Modifier
                .width(600.dp)
                .height(600.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.wrapContentSize(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.wrapContentWidth()
                        .padding(bottom = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(copywriter.getText("Please_scan_the_binding_device"),
                        modifier = Modifier.wrapContentWidth(),
                        fontSize = 24.sp,
                        softWrap = false,
                        color = Color(red = 84, green = 135, blue = 237)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = TablerIcons.Scan,
                        contentDescription = "Scan",
                        modifier = Modifier.size(28.dp),
                        tint = Color(red = 84, green = 135, blue = 237)
                    )
                }
                qrImage?.let {
                    Image(
                        modifier = Modifier.size(qrSize.width),
                        bitmap = it,
                        contentDescription = "QR Code",
                    )
                } ?: run {

                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    Box(modifier = Modifier.size(qrSize.width),
                        contentAlignment = Alignment.Center) {
                        Icon(
                            modifier = Modifier.size(100.dp)
                                .graphicsLayer(rotationZ = rotation),
                            painter = autoRenew(),
                            contentDescription = "QR Code",
                            tint = MaterialTheme.colors.onBackground
                        )
                    }
                }
            }
        }
    }
}