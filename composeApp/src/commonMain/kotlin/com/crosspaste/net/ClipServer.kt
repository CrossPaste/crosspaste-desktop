package com.crosspaste.net

interface ClipServer {
    fun start(): ClipServer

    fun stop()

    fun port(): Int
}
