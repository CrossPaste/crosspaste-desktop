package com.crosspaste.app

class DesktopAppTokenService(
    private val appWindowManager: DesktopAppWindowManager,
) : AppTokenService() {

    override fun preShowToken() {
        appWindowManager.showMainWindow()
    }
}
