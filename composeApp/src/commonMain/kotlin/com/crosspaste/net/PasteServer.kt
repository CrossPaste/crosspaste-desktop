package com.crosspaste.net

interface PasteServer {
    fun start(): PasteServer

    fun stop()

    fun port(): Int
}
