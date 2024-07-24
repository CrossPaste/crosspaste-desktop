package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import com.crosspaste.CrossPaste.Companion.koinApplication
import com.crosspaste.app.AbstractAppWindowManager.Companion.SEARCH_WINDOW_TITLE
import com.crosspaste.app.AppWindowManager
import com.crosspaste.ui.search.CrossPasteSearchWindowContent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun ApplicationScope.CrossPasteSearchWindow(windowIcon: Painter?) {
    val appWindowManager = koinApplication.koin.get<AppWindowManager>()

    Window(
        onCloseRequest = ::exitApplication,
        visible = appWindowManager.showSearchWindow,
        state = appWindowManager.searchWindowState,
        title = SEARCH_WINDOW_TITLE,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            appWindowManager.searchComposeWindow = window

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        appWindowManager.showSearchWindow = true
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        appWindowManager.showSearchWindow = false
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                appWindowManager.searchComposeWindow = null
                window.removeWindowFocusListener(windowListener)
            }
        }

        CrossPasteSearchWindowContent()
    }
}
