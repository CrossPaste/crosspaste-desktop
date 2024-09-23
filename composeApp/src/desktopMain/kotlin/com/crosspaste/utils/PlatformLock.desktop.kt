package com.crosspaste.utils

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual fun createPlatformLock(): PlatformLock {
    return DesktopPlatformLock()
}

class DesktopPlatformLock : PlatformLock {
    private val lock = ReentrantLock()

    override fun <T> withLock(action: () -> T): T {
        return lock.withLock(action)
    }

    override fun lock() {
        lock.lock()
    }

    override fun unlock() {
        lock.unlock()
    }
}
