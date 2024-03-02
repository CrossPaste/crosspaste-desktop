package com.clipevery.ui.search

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.clip.ClipSearchService
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener


fun createSearchWindow(clipSearchService: ClipSearchService) {
    val appUI = clipSearchService.getAppUI()
    if (clipSearchService.tryStart()) {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                visible = appUI.showSearchWindow,
                title = "Spotlight Search",
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

                SearchUI()
            }
        }
    } else {
        appUI.showSearchWindow = true
    }
}

@Composable
fun SearchUI() {
    Text("show search")
}