package com.clipevery.listener

interface GlobalListener {

    fun isRegistered(): Boolean

    fun start()

    fun stop()
}
