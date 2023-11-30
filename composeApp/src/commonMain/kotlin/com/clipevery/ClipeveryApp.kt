package com.clipevery

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.clipevery.ui.AboutUI
import com.clipevery.ui.HomeUI
import com.clipevery.ui.SettingsUI
import org.koin.core.KoinApplication

@Composable
fun ClipeveryApp(koinApplication: KoinApplication, hideWindow: () -> Unit) {
    MaterialTheme {
        Box(modifier = Modifier
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        hideWindow()
                    },
                    onTap = {
                        hideWindow()
                    },
                    onLongPress = {
                        hideWindow()
                    },
                    onPress = {
                        hideWindow()
                    },
                )
            }
            .clip(RoundedCornerShape(10.dp))
            .fillMaxSize()
            .padding(10.dp, 0.dp, 10.dp, 10.dp),
            contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .shadow(5.dp, RoundedCornerShape(10.dp), false)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {},
                            onTap = {},
                            onLongPress = {},
                            onPress = {},
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    ClipeveryCommon(koinApplication)
                }
            }
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
    val currentPage = remember { mutableStateOf(PageType.HOME) }
    when (currentPage.value) {
        PageType.HOME -> {
            HomeUI(currentPage)
        }
        PageType.SETTINGS -> {
            SettingsUI(currentPage)
        }
        PageType.ABOUT -> {
            AboutUI(currentPage)
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

enum class PageType {
    HOME, SETTINGS, ABOUT
}