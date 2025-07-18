package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste
import com.crosspaste.app.generated.resources.crosspaste_mac
import com.crosspaste.platform.Platform
import com.crosspaste.ui.tray.LinuxTrayView
import com.crosspaste.ui.tray.MacTrayView
import com.crosspaste.ui.tray.WindowsTrayView
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
fun CrossPasteWindows(exiting: Boolean) {
    val platform = koinInject<Platform>()
    val isMacos by remember { mutableStateOf(platform.isMacos()) }
    val isWindows by remember { mutableStateOf(platform.isWindows()) }
    val isLinux by remember { mutableStateOf(platform.isLinux()) }

    val windowIcon: Painter? =
        if (platform.isMacos()) {
            painterResource(Res.drawable.crosspaste_mac)
        } else if (platform.isWindows() || platform.isLinux()) {
            painterResource(Res.drawable.crosspaste)
        } else {
            null
        }

    if (!exiting) {
        if (isMacos) {
            MacTrayView.Tray()
        } else if (isWindows) {
            WindowsTrayView.Tray()
        } else if (isLinux) {
            LinuxTrayView.Tray()
        }
    }

    MainWindow(windowIcon)

    SearchWindow(windowIcon)
}
