package com.clipevery.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowState

interface AppWindowManager {

    var showMainWindow: Boolean

    var mainWindowState: WindowState

    var showMainDialog: Boolean

    val windowManager: WindowManager

    var showSearchWindow: Boolean

    var searchWindowState: WindowState

    val searchWindowDetailViewDpSize: DpSize

    var showToken: Boolean

    var token: CharArray

    fun startRefreshToken()

    fun stopRefreshToken()

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
