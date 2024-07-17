package com.crosspaste.utils

import java.util.concurrent.locks.ReentrantLock

actual fun createPlatformLock(): PlatformLock {
    return DesktopPlatformLock()
}

class DesktopPlatformLock : PlatformLock {
    private val lock = ReentrantLock()

    override fun lock() {
        lock.lock()
    }

    override fun unlock() {
        lock.unlock()
    }
}
