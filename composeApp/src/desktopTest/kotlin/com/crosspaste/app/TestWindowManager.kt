package com.crosspaste.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TestWindowManager(
    private val mockOS: MockOS,
) : DesktopAppWindowManager() {

    var prevApp: String? by mutableStateOf(null)

    var pasterId: Int = 0

    var currentTitle: String? by mutableStateOf(null)

    override fun getPrevAppName(): String? {
        return prevApp
    }

    override fun getCurrentActiveAppName(): String? {
        return mockOS.currentApp
    }

    override suspend fun activeMainWindow() {
        showMainWindow = true
        bringToFront(MAIN_WINDOW_TITLE)
    }

    override suspend fun unActiveMainWindow() {
        bringToBack(MAIN_WINDOW_TITLE, false)
        showMainWindow = false
    }

    override suspend fun activeSearchWindow() {
        showSearchWindow = true

        bringToFront(SEARCH_WINDOW_TITLE)
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        val toPaste = preparePaste()
        bringToBack(SEARCH_WINDOW_TITLE, toPaste)
        showSearchWindow = false
    }

    private fun bringToFront(windowTitle: String) {
        currentTitle = windowTitle
        if (mockOS.currentApp != "CrossPaste") {
            prevApp = mockOS.currentApp
        }
        mockOS.currentApp = "CrossPaste"
    }

    private suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        currentTitle = windowTitle
        mockOS.currentApp = prevApp
        if (toPaste) {
            toPaste()
        }
    }

    override suspend fun toPaste() {
        pasterId++
    }
}

class MockOS(var currentApp: String? = null)
