package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import com.crosspaste.CrossPaste.Companion.koinApplication
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopAppWindowManager.Companion.SEARCH_WINDOW_TITLE
import com.crosspaste.ui.search.CrossPasteSearchWindowContent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun ApplicationScope.CrossPasteSearchWindow(windowIcon: Painter?) {
    val appWindowManager = koinApplication.koin.get<DesktopAppWindowManager>()

    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()
    val currentSearchWindowState by appWindowManager.searchWindowState.collectAsState()

    Window(
        onCloseRequest = ::exitApplication,
        visible = showSearchWindow,
        state = currentSearchWindowState,
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
                        appWindowManager.setShowSearchWindow(true)
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        appWindowManager.setShowSearchWindow(false)
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
