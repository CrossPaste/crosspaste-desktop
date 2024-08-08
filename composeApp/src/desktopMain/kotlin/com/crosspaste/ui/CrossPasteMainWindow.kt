package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import com.crosspaste.CrossPaste.Companion.koinApplication
import com.crosspaste.CrossPasteMainWindowContent
import com.crosspaste.app.AbstractAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.crosspaste.app.AppWindowManager
import com.crosspaste.listener.GlobalListener
import com.crosspaste.ui.LinuxTrayView.initSystemTray
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import dorkbox.systemTray.SystemTray
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun CrossPasteMainWindow(
    exitApplication: () -> Unit,
    systemTray: SystemTray?,
    windowIcon: Painter?,
) {
    val appWindowManager = koinApplication.koin.get<AppWindowManager>()
    val globalListener = koinApplication.koin.get<GlobalListener>()

    Window(
        onCloseRequest = exitApplication,
        visible = appWindowManager.showMainWindow,
        state = appWindowManager.mainWindowState,
        title = MAIN_WINDOW_TITLE,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            appWindowManager.mainComposeWindow = window

            systemTray?.let { tray ->
                initSystemTray(tray, koinApplication, exitApplication)
            }

            globalListener.start()

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        appWindowManager.showMainWindow = true
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                            if (appWindowManager.showMainWindow &&
                                !appWindowManager.showMainDialog &&
                                !appWindowManager.showFileDialog
                            ) {
                                appWindowManager.unActiveMainWindow()
                            }
                        }
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                appWindowManager.mainComposeWindow = null
                window.removeWindowFocusListener(windowListener)
            }
        }
        CrossPasteMainWindowContent { appWindowManager.unActiveMainWindow() }
    }
}
