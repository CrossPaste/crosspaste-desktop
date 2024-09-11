package com.crosspaste.utils

expect fun createPlatformLock(): PlatformLock

interface PlatformLock {
    fun lock()

    fun unlock()

    fun withLock(action: () -> Unit)
}
