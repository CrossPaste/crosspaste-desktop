package com.crosspaste.app

interface AppTokenService {

    var showToken: Boolean

    var token: CharArray

    fun startRefreshToken()

    fun stopRefreshToken()
}
