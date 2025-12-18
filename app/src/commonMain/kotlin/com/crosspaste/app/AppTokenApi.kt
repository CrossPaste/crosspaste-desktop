package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow

interface AppTokenApi {

    val token: StateFlow<CharArray>

    val showToken: StateFlow<Boolean>

    val refreshProgress: StateFlow<Float>

    val refresh: StateFlow<Boolean>

    fun sameToken(token: Int): Boolean

    fun startRefresh(showToken: Boolean)

    fun stopRefresh(hideToken: Boolean)
}
