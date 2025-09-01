package com.crosspaste.net

interface PasteBonjourService {

    fun request(appInstanceId: String)

    fun close()
}
