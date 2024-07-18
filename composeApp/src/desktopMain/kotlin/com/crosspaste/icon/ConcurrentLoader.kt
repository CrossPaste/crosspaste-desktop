package com.crosspaste.icon

import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createPlatformLock

interface ConcurrentLoader<T, R> : IconLoader<T, R> {

    val lockMap: ConcurrentPlatformMap<R, PlatformLock>

    fun resolve(key: String): R

    fun loggerWarning(
        value: T,
        e: Exception,
    )

    fun exist(result: R): Boolean

    fun save(
        key: String,
        value: T,
        result: R,
    )

    fun convertToKey(value: T): String

    override fun load(value: T): R? {
        try {
            val key = convertToKey(value)
            val result = resolve(key)
            if (exist(result)) {
                return result
            }
            val lock = lockMap.computeIfAbsent(result) { createPlatformLock() }
            lock.lock()
            try {
                if (exist(result)) {
                    return result
                }
                save(key, value, result)
                return if (exist(result)) {
                    result
                } else {
                    null
                }
            } finally {
                lock.unlock()
            }
        } catch (e: Exception) {
            loggerWarning(value, e)
            return null
        }
    }
}
