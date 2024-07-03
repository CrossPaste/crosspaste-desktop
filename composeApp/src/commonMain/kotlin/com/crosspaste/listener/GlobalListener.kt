package com.crosspaste.listener

interface GlobalListener {

    fun isRegistered(): Boolean

    fun start()

    fun stop()
}
