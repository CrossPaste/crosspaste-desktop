package com.crosspaste.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.DesktopConfigManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class TestWindowManager(
    appSize: DesktopAppSize,
    configManager: DesktopConfigManager,
    private val mockOS: MockOS,
) : DesktopAppWindowManager(appSize, configManager) {

    private var prevApp: MutableStateFlow<String?> = MutableStateFlow(null)

    var pasterId: Int = 0

    private var currentTitle: String? by mutableStateOf(null)

    override fun getPrevAppName(): Flow<String?> = prevApp

    override fun getCurrentActiveAppName(): String? = mockOS.currentApp

    override suspend fun recordActiveInfoAndShowMainWindow(useShortcutKeys: Boolean) {
        showMainWindow()
        bringToFront(mainWindowTitle)
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        val toPaste = preparePaste()
        bringToBack(mainWindowTitle, toPaste)
        this@TestWindowManager.hideMainWindow()
    }

    override suspend fun recordActiveInfoAndShowSearchWindow(useShortcutKeys: Boolean) {
        showSearchWindow()
        bringToFront(searchWindowTitle)
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        val toPaste = preparePaste(0)
        bringToBack(searchWindowTitle, toPaste)
        for (i in 1 until size) {
            delay(1000)
            if (preparePaste(i)) {
                toPaste()
            }
        }
        hideSearchWindow()
    }

    private fun bringToFront(windowTitle: String) {
        currentTitle = windowTitle
        if (mockOS.currentApp != "CrossPaste") {
            prevApp.value = mockOS.currentApp
        }
        mockOS.currentApp = "CrossPaste"
    }

    private suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        currentTitle = windowTitle
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
