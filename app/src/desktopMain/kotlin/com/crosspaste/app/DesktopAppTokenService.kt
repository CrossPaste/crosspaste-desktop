package com.crosspaste.app

import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.PairingCode
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.launch

class DesktopAppTokenService(
    private val appWindowManager: DesktopAppWindowManager,
    private val navigationManager: NavigationManager,
) : AppTokenService() {

    override fun preShowToken() {
        mainCoroutineDispatcher.launch {
            appWindowManager.showMainWindow(WindowTrigger.SYSTEM)
        }
    }

    override fun preShowPairingCode() {
        mainCoroutineDispatcher.launch {
            navigationManager.navigateAndClearStack(PairingCode)
            appWindowManager.showMainWindow(WindowTrigger.SYSTEM)
        }
    }
}
