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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.ui.TabsUI
import com.clipevery.ui.TitleUI
import compose.icons.TablerIcons
import compose.icons.tablericons.Scan
import org.koin.core.KoinApplication

@Composable
fun ClipeveryApp(koinApplication: KoinApplication) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(238, 238, 238)),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ClipeveryCommon(koinApplication)
        }
    }
}


@Composable
fun ClipeveryCommon(koinApplication: KoinApplication) {
    CompositionLocalProvider(
        LocalKoinApplication provides koinApplication
    ) {
        ClipeveryWithProvidedDependencies()
    }
}

@Composable
fun ClipeveryWithProvidedDependencies() {
    CustomWindowDecoration()
    TabsUI()

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
        TitleUI()
    }
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