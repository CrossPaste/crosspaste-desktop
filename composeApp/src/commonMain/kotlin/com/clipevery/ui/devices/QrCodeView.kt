package com.clipevery.ui.devices

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppUI
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.utils.QRCodeGenerator
import compose.icons.TablerIcons
import compose.icons.tablericons.Scan


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

    val qrImage = remember(width, height, appUI.token) {
        qrCodeGenerator.generateQRCode(width, height, appUI.token)
    }

    LaunchedEffect(Unit) {
        appUI.startRefreshToken()
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
            Row(modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = (+82).dp)
                .wrapContentWidth(),
                horizontalArrangement = Arrangement.Center) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐所有子组件
                    horizontalArrangement = Arrangement.Center // 水平居中排列
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
            }
            Image(
                bitmap = qrImage,
                contentDescription = "QR Code",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}