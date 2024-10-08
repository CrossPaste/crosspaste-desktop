package com.crosspaste.app

interface AppTokenService {

    var showTokenProgress: Float

    var showToken: Boolean

    var token: CharArray

    fun toShowToken()

    fun startRefreshToken()

    fun stopRefreshToken()
}
