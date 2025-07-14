package com.crosspaste.utils

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual fun createPlatformLock(): PlatformLock = DesktopPlatformLock()

class DesktopPlatformLock : PlatformLock {
    private val lock = ReentrantLock()

    override fun <T> withLock(action: () -> T): T = lock.withLock(action)

    override fun lock() {
        lock.lock()
    }

    override fun unlock() {
        lock.unlock()
    }
}
