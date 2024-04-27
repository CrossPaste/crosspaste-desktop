package com.clipevery.app

import com.clipevery.app.DesktopAppWindowManager.searchWindowTitle
import com.clipevery.os.windows.api.User32
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.delay

class WinWindowManager : WindowManager {

    private var prevHWND: HWND? = null

    override suspend fun bringToFront(windowTitle: String) {
        logger.info { "$windowTitle bringToFront Clipevery" }
        if (windowTitle == searchWindowTitle) {
            // to wait for the search window to be ready
            delay(500)
        }
        prevHWND = User32.bringToFront(windowTitle)
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        logger.info { "$windowTitle bringToBack Clipevery" }
        User32.bringToBack(windowTitle, prevHWND, toPaste)
    }
}
