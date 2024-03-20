package com.clipevery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.ui.ClipeveryTheme
import org.koin.core.KoinApplication
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener


fun createSearchWindow(clipSearchService: ClipSearchService,
                       koinApplication: KoinApplication) {
    val appUI = clipSearchService.getAppUI()
    if (clipSearchService.tryStart()) {
        application {

            val windowState = rememberWindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition.Aligned(Alignment.Center),
                size = DpSize(800.dp, 60.dp)
            )

            Window(
                onCloseRequest = ::exitApplication,
                visible = appUI.showSearchWindow,
                state = windowState,
                title = "Clipevery Search",
                alwaysOnTop = true,
                undecorated = true,
                transparent = true,
                resizable = false
                ) {

                LaunchedEffect(Unit) {
                    window.addWindowFocusListener(object : WindowFocusListener {
                        override fun windowGainedFocus(e: WindowEvent?) {
                            appUI.showSearchWindow = true
                        }

                        override fun windowLostFocus(e: WindowEvent?) {
                            appUI.showSearchWindow = false
                        }
                    })

                }

                ClipeveryAppSearchView(
                    koinApplication,
                    hideWindow = { appUI.showSearchWindow = false }
                )
            }
        }
    } else {
        appUI.showSearchWindow = true
    }
}

@Composable
fun ClipeveryAppSearchView(koinApplication: KoinApplication,
                            hideWindow: () -> Unit,
                           ) {
    CompositionLocalProvider(
        LocalKoinApplication provides koinApplication,
    ) {
        ClipeverySearchWindow(hideWindow)
    }
}

@Composable
fun ClipeverySearchWindow(hideWindow: () -> Unit) {
    ClipeveryTheme {
        Box(modifier = Modifier.width(800.dp)
            .height(60.dp)
            .background(MaterialTheme.colors.background)
        ) {

        }
    }
}