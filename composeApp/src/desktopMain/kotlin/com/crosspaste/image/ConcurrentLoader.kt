package com.crosspaste.image

import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createPlatformLock

interface ConcurrentLoader<T, R> : ImageLoader<T, R> {

    val lockMap: ConcurrentPlatformMap<String, PlatformLock>

    fun resolve(
        key: String,
        value: T,
    ): R

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
            val result = resolve(key, value)
            if (exist(result)) {
                return result
            }
            val lock = lockMap.computeIfAbsent(key) { createPlatformLock() }
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
