package com.crosspaste.utils

import java.util.concurrent.locks.ReentrantLock

actual fun createPlatformLock(): PlatformLock {
    return PlatformLock()
}

class PlatformLock {
    private val lock = ReentrantLock()

    fun lock() {
        lock.lock()
    }

    fun unlock() {
        lock.unlock()
    }
}
