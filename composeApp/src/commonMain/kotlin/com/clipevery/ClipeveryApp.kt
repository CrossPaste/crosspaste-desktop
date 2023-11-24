package com.clipevery

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Scan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClipeveryApp(dependencies: Dependencies) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(238, 238, 238)),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ClipeveryCommon(dependencies)
        }
    }
}


@Composable
fun ClipeveryCommon(dependencies: Dependencies) {
    CompositionLocalProvider(
        LocalClipeveryServer provides dependencies.clipServer,
        LocalConfigManager provides dependencies.configManager,
        LocalFilePersist provides dependencies.filePersist,
        LocalSignalProtocol provides dependencies.signalProtocol,
        LocalQRCodeGenerator provides dependencies.qrCodeGenerator,
    ) {
        ClipeveryWithProvidedDependencies()
    }
}

@Composable
fun ClipeveryWithProvidedDependencies() {
    val configManager = LocalConfigManager.current
    val config = remember { mutableStateOf(configManager.config) }
    CustomWindowDecoration()
    if (!config.value.bindingState) {
        bindingQRCode()
    } else {
        mainUI()
    }
}

@Composable
fun CustomWindowDecoration() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        color = MaterialTheme.colors.background,
        shape = RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
    ) {
        Box(
            modifier = Modifier.background(Color.Black)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        startY = 0.0f,
                        endY = 3.0f
                    )
                ),
        ) {
            // Custom title bar content
        }
    }
}


@Composable
fun bindingQRCode() {
    val qrCodeGenerator = LocalQRCodeGenerator.current
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
                    Text("请扫描绑定设备",
                        modifier = Modifier.wrapContentWidth(),
                        fontSize = 24.sp,
                        softWrap = false,
                        color = Color(red = 84, green = 135, blue = 237)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = TablerIcons.Scan,
                        contentDescription = "Scan",
                        modifier = Modifier.size(28.dp),
                        tint = Color(red = 84, green = 135, blue = 237))
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

@Composable
fun mainUI() {
    Text("mainUI")
}


@Preview
@Composable
fun ColumTest() {
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
                    Text("请扫描绑定设备",
                        modifier = Modifier.wrapContentWidth(),
                        fontSize = 24.sp,
                        softWrap = false,
                        color = Color(red = 84, green = 135, blue = 237)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = TablerIcons.Scan,
                        contentDescription = "Scan",
                        modifier = Modifier.size(28.dp),
                        tint = Color(red = 84, green = 135, blue = 237))
                }
            }
            Image(
                bitmap = loadImageBitmap("clipevery_icon.png"),
                contentDescription = "QR Code",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

fun loadImageBitmap(resourcePath: String): ImageBitmap {
    // Assuming `resourcePath` is a valid path for an image file within your resources directory.
    val image = org.jetbrains.skia.Image.makeFromEncoded(
        Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        ?.readBytes()!!)
    return image.toComposeImageBitmap()
}