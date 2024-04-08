package com.clipevery.net

interface ClipServer {
    fun start(): ClipServer

    fun stop()

    fun port(): Int
}
