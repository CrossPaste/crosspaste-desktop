package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.crosspaste.app.ExitMode
import com.crosspaste.listener.GlobalListener
import com.crosspaste.ui.base.DesktopUISupport
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun MainWindow(
    exitApplication: (ExitMode) -> Unit,
    windowIcon: Painter?,
) {
    val appFileChooser = koinInject<AppFileChooser>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val globalListener = koinInject<GlobalListener>()
    val uiSupport = koinInject<UISupport>() as DesktopUISupport

    val mainWindowState by appWindowManager.mainWindowState.collectAsState()
    val showFileDialog by appFileChooser.showFileDialog.collectAsState()
    val showMainDialog by appWindowManager.showMainDialog.collectAsState()
    val showMainWindow by appWindowManager.showMainWindow.collectAsState()
    val showColorChooser by uiSupport.showColorChooser.collectAsState()

    Window(
        onCloseRequest = { exitApplication(ExitMode.EXIT) },
        visible = showMainWindow,
        state = mainWindowState,
        title = MAIN_WINDOW_TITLE,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            appWindowManager.mainComposeWindow = window

            globalListener.start()

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        mainCoroutineDispatcher.launch {
                            appWindowManager.setShowMainWindow(true)
                        }
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        mainCoroutineDispatcher.launch {
                            if (showMainWindow &&
                                !showMainDialog &&
                                !showFileDialog &&
                                !showColorChooser
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
        CrossPasteMainWindowContent()
    }
}
