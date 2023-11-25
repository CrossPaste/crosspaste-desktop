package com.clipevery.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.utils.QRCodeGenerator
import compose.icons.TablerIcons
import compose.icons.tablericons.Scan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun bindingQRCode() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val qrCodeGenerator = current.koin.get<QRCodeGenerator>()
    val coroutineScope = rememberCoroutineScope()
    var refreshJob: Job? by remember { mutableStateOf(null) }

    var qrImage by remember { mutableStateOf(qrCodeGenerator.generateQRCode(600, 600)) }

    DisposableEffect(Unit) {
        refreshJob = coroutineScope.launch {
            while (true) {
                qrImage = qrCodeGenerator.generateQRCode( 600, 600)
                delay(5000)
            }
        }
        onDispose {
            refreshJob?.cancel()
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