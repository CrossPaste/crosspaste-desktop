package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow

interface AppTokenApi {

    val token: StateFlow<CharArray>

    val showTokenProgression: StateFlow<Float>

    val showToken: StateFlow<Boolean>

    fun sameToken(token: Int): Boolean

    fun toShowToken(showView: Boolean = false)

    fun toHideToken()
}
