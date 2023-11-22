package com.clipevery.net

import com.clipevery.model.AppRequestBindInfo

interface ClipServer {
    fun start(): ClipServer

    fun stop()

    fun port(): Int

    fun appRequestBindInfo(): AppRequestBindInfo
}

