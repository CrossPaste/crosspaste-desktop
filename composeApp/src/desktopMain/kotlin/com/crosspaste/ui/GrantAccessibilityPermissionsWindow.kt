package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.ui.base.CrossPasteGrantAccessibilityPermissions

@Composable
fun ApplicationScope.GrantAccessibilityPermissionsWindow(windowIcon: Painter?) {
    val windowState =
        rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.PlatformDefault,
            size = DpSize(width = 360.dp, height = 200.dp),
        )

    Window(
        onCloseRequest = ::exitApplication,
        visible = true,
        state = windowState,
        title = "Apply Accessibility Permissions",
        icon = windowIcon,
        alwaysOnTop = true,
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

        CrossPasteGrantAccessibilityPermissions {
            MacosApi.INSTANCE.checkAccessibilityPermissions()
        }
    }
}
