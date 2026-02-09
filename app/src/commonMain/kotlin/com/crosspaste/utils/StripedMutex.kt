package com.crosspaste.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StripedMutex(
    private val stripeCount: Int = 32,
) {
    private val mutexes = Array(stripeCount) { Mutex() }

    private fun getMutex(key: Any): Mutex {
        val hash = key.hashCode() and Int.MAX_VALUE
        return mutexes[hash % stripeCount]
    }

    suspend fun <T> withLock(
        key: Any,
        action: suspend () -> T,
    ): T = getMutex(key).withLock { action() }
}
