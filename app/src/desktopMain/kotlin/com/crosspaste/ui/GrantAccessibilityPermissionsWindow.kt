package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.ui.base.CrossPasteGrantAccessibilityPermissions
import org.koin.compose.koinInject

@Composable
fun ApplicationScope.GrantAccessibilityPermissionsWindow(windowIcon: Painter?) {
    val appSize = koinInject<DesktopAppSize>()
    val windowState =
        rememberDialogState(
            size = appSize.grantAccessibilityPermissionsWindowsSize,
        )

    var alwaysOnTop by remember { mutableStateOf(true) }

    DialogWindow(
        onCloseRequest = ::exitApplication,
        visible = true,
        state = windowState,
        title = "Apply Accessibility Permissions",
        icon = windowIcon,
        alwaysOnTop = alwaysOnTop,
        undecorated = false,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            window.rootPane.apply {
                rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }

            onDispose {}
        }

        CrossPasteGrantAccessibilityPermissions(
            checkAccessibilityPermissionsFun = {
                MacosApi.INSTANCE.checkAccessibilityPermissions()
            },
            setOnTop = {
                alwaysOnTop = it
            },
        )
    }
}
