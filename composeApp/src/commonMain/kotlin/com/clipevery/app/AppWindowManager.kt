package com.clipevery.app

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowState

interface AppWindowManager {

    var showMainWindow: Boolean

    var mainWindowState: WindowState

    var showMainDialog: Boolean

    var showSearchWindow: Boolean

    var searchWindowState: WindowState

    var searchFocusRequester: FocusRequester

    val searchWindowDetailViewDpSize: DpSize

    fun getCurrentActiveAppName(): String?

    fun activeMainWindow()

    fun unActiveMainWindow()

    fun switchMainWindow() {
        if (showMainWindow) {
            unActiveMainWindow()
        } else {
            activeMainWindow()
        }
    }

    suspend fun activeSearchWindow()

    suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean)

    fun getPrevAppName(): String?

    suspend fun toPaste()
}
