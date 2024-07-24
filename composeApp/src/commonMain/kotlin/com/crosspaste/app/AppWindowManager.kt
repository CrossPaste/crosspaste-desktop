package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowState

interface AppWindowManager {

    var showMainWindow: Boolean

    var mainWindowState: WindowState

    var mainComposeWindow: ComposeWindow?

    var mainFocusRequester: FocusRequester

    var showMainDialog: Boolean

    var showSearchWindow: Boolean

    var searchWindowState: WindowState

    var searchComposeWindow: ComposeWindow?

    var searchFocusRequester: FocusRequester

    val searchWindowDetailViewDpSize: DpSize

    fun getCurrentActiveAppName(): String?

    suspend fun activeMainWindow()

    suspend fun unActiveMainWindow()

    suspend fun switchMainWindow() {
        if (showMainWindow) {
            unActiveMainWindow()
        } else {
            activeMainWindow()
        }
    }

    fun setMainCursorWait()

    fun resetMainCursor()

    suspend fun activeSearchWindow()

    suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean)

    fun resetSearchCursor()

    fun setSearchCursorWait()

    fun getPrevAppName(): String?

    suspend fun toPaste()
}
