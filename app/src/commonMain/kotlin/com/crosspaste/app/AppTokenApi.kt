package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow

interface AppTokenApi {

    val token: StateFlow<CharArray>

    val showToken: StateFlow<Boolean>

    val refreshProgress: StateFlow<Float>

    val refresh: StateFlow<Boolean>

    val pendingVerifiers: StateFlow<Set<String>>

    fun sameToken(token: Int): Boolean

    fun setSASToken(sas: Int)

    fun startRefresh(showToken: Boolean)

    fun stopRefresh(hideToken: Boolean)

    fun addPendingVerifier(appInstanceId: String)

    fun removePendingVerifier(appInstanceId: String)
}
