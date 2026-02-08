package com.crosspaste.net

import kotlinx.coroutines.flow.StateFlow

interface Server {

    suspend fun start()

    suspend fun stop()

    fun port(): Int

    val portFlow: StateFlow<Int>
}
