package com.crosspaste.net

interface Server {

    suspend fun start()

    suspend fun stop()

    fun port(): Int
}
