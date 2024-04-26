package com.clipevery.app

import androidx.compose.ui.unit.DpSize

interface AppWindowManager {

    var showMainWindow: Boolean

    val mainWindowDpSize: DpSize

    var showSearchWindow: Boolean

    val searchWindowDpSize: DpSize

    var showToken: Boolean

    var token: CharArray

    fun startRefreshToken()

    fun stopRefreshToken()
}
