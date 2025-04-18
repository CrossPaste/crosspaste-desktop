package com.crosspaste.app

class DesktopAppTokenService(
    private val appWindowManager: DesktopAppWindowManager,
) : AppTokenService() {

    override fun toShowToken() {
        appWindowManager.setShowMainWindow(true)
        super.toShowToken()
    }

    override fun toHideToken(hideWindow: Boolean) {
        if (hideWindow) {
            appWindowManager.setShowMainWindow(false)
        }
        super.toHideToken(hideWindow)
    }
}
