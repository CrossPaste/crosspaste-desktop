package com.clipevery.app

interface AppTokenService {

    var showToken: Boolean

    var token: CharArray

    fun startRefreshToken()

    fun stopRefreshToken()
}
