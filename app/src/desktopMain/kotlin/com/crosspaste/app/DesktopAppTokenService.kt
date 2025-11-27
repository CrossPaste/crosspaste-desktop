package com.crosspaste.app

import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.launch

class DesktopAppTokenService(
    private val appWindowManager: DesktopAppWindowManager,
) : AppTokenService() {

    override fun preShowToken() {
        mainCoroutineDispatcher.launch {
            appWindowManager.showMainWindow(
                recordInfo = false,
                useShortcutKeys = false,
            )
        }
    }
}
