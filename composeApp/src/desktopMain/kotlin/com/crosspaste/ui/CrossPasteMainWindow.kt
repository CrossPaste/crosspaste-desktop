package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.crosspaste.app.ExitMode
import com.crosspaste.listener.GlobalListener
import com.crosspaste.ui.base.DesktopUISupport
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun CrossPasteMainWindow(
    exitApplication: (ExitMode) -> Unit,
    windowIcon: Painter?,
) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val globalListener = koinInject<GlobalListener>()
    val uiSupport = koinInject<UISupport>() as DesktopUISupport

    val showMainWindow by appWindowManager.showMainWindow.collectAsState()
    val currentMainWindowState by appWindowManager.mainWindowState.collectAsState()
    val showColorChooser by uiSupport.showColorChooser.collectAsState()

    Window(
        onCloseRequest = { exitApplication(ExitMode.EXIT) },
        visible = showMainWindow,
        state = currentMainWindowState,
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
                        appWindowManager.setShowMainWindow(true)
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                            if (appWindowManager.getShowMainWindow() &&
                                !appWindowManager.getShowMainDialog() &&
                                !appWindowManager.getShowFileDialog() &&
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
