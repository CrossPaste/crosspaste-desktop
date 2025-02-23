package com.crosspaste.net

interface Server {

    fun start()

    fun stop()

    fun port(): Int
}
