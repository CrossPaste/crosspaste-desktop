package com.clipevery.app

import androidx.compose.ui.unit.DpSize

interface AppWindowManager {

    var showMainWindow: Boolean

    val mainWindowTitle: String

    val mainWindowDpSize: DpSize

    var showSearchWindow: Boolean

    val searchWindowTitle: String

    val searchWindowDpSize: DpSize

    val searchWindowDetailViewDpSize: DpSize

    var showToken: Boolean

    var token: CharArray

    fun startRefreshToken()

    fun stopRefreshToken()

    fun getCurrentActiveApp(): String?

    fun activeMainWindow()

    suspend fun activeSearchWindow()

    fun unActiveMainWindow()

    suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean)
}
