package com.crosspaste.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste
import com.crosspaste.app.generated.resources.crosspaste_mac
import com.crosspaste.platform.Platform
import com.crosspaste.ui.tray.MacTrayView
import com.crosspaste.ui.tray.NonMacTrayView
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
fun ApplicationScope.CrossPasteWindows(exiting: Boolean) {
    val platform = koinInject<Platform>()
    val isMacos = remember { platform.isMacos() }
    val isWindows = remember { platform.isWindows() }
    val isLinux = remember { platform.isLinux() }

    val windowIcon: Painter? =
        if (isMacos) {
            painterResource(Res.drawable.crosspaste_mac)
        } else if (isWindows || isLinux) {
            painterResource(Res.drawable.crosspaste)
        } else {
            null
        }

    if (!exiting) {
        if (isMacos) {
            MacTrayView.Tray()
        } else if (windowIcon != null) {
            NonMacTrayView(windowIcon)
        }
    }

    MainWindow(windowIcon)

    // The side search list's scroll state is hoisted here, the common parent of both the search
    // window and the bubble window, so it lives in the UI layer (not the ViewModel) yet stays shared
    // across both windows. It also survives the search window content's composition pause while the
    // window is hidden, which a remember inside that content would not.
    val searchListState = remember { LazyListState() }

    SearchWindow(windowIcon, searchListState)

    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val bubbleWindowInfo by appWindowManager.bubbleWindowInfo.collectAsState()
    if (bubbleWindowInfo.show) {
        BubbleWindow(windowIcon, searchListState)
    }
}
