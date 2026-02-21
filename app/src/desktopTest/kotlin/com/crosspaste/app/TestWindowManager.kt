package com.crosspaste.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class TestWindowManager(
    appSize: DesktopAppSize,
    private val mockOS: MockOS,
) : DesktopAppWindowManager(appSize) {

    private var prevApp: MutableStateFlow<String?> = MutableStateFlow(null)

    var pasterId: Int = 0

    override fun getPrevAppName(): Flow<String?> = prevApp

    override fun getCurrentActiveAppName(): String? = mockOS.currentApp

    override fun getRunningAppNames(): List<String> = emptyList()

    override fun startWindowService() {}

    override fun stopWindowService() {}

    override fun saveCurrentActiveAppInfo() {
    }

    override suspend fun focusMainWindow(windowTrigger: WindowTrigger) {}

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {}

    override suspend fun focusBubbleWindow() {}

    fun saveActiveAppInfo(appName: String?) {
        if (mockOS.currentApp != "CrossPaste") {
            prevApp.value = mockOS.currentApp
        }
        mockOS.currentApp = appName
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        val toPaste = preparePaste()
        bringToBack(toPaste)
        this@TestWindowManager.hideMainWindow()
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        val toPaste = preparePaste(0)
        bringToBack(toPaste)
        for (i in 1 until size) {
            delay(1000)
            if (preparePaste(i)) {
                toPaste()
            }
        }
        hideSearchWindow()
    }

    private suspend fun bringToBack(toPaste: Boolean) {
        mockOS.currentApp = prevApp.value
        if (toPaste) {
            toPaste()
        }
    }

    override suspend fun toPaste() {
        pasterId++
    }
}

class MockOS(
    var currentApp: String? = null,
)
