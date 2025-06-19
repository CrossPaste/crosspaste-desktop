package com.crosspaste.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.DesktopConfigManager
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

    override fun getPrevAppName(): Flow<String?> {
        return prevApp
    }

    override fun getCurrentActiveAppName(): String? {
        return mockOS.currentApp
    }

    override suspend fun activeMainWindow() {
        setShowMainWindow(true)
        bringToFront(MAIN_WINDOW_TITLE)
    }

    override suspend fun unActiveMainWindow(preparePaste: suspend () -> Boolean) {
        val toPaste = preparePaste()
        bringToBack(MAIN_WINDOW_TITLE, toPaste)
        setShowMainWindow(false)
    }

    override suspend fun activeSearchWindow() {
        setShowSearchWindow(true)
        bringToFront(SEARCH_WINDOW_TITLE)
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        val toPaste = preparePaste()
        bringToBack(SEARCH_WINDOW_TITLE, toPaste)
        setShowSearchWindow(false)
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

class MockOS(var currentApp: String? = null)
