package com.clipevery.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition

interface AppWindowManager {

    var showMainWindow: Boolean

    val mainWindowTitle: String

    var mainWindowPosition: WindowPosition

    val mainWindowDpSize: DpSize

    var showMainDialog: Boolean

    var showSearchWindow: Boolean

    val searchWindowTitle: String

    val searchWindowPosition: WindowPosition

    val searchWindowDpSize: DpSize

    val searchWindowDetailViewDpSize: DpSize

    var showToken: Boolean

    var token: CharArray

    fun startRefreshToken()

    fun stopRefreshToken()

    fun getCurrentActiveAppName(): String?

    fun activeMainWindow()

    suspend fun activeSearchWindow()

    fun unActiveMainWindow()

    suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean)

    fun getPrevAppName(): String?

    suspend fun toPaste()
}
