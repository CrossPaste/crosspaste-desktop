package com.crosspaste.image

import com.crosspaste.utils.Loader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ConcurrentLoader<T, R> : Loader<T, R> {

    val mutex: Mutex

    fun resolve(
        key: String,
        value: T,
    ): R

    fun loggerWarning(
        value: T,
        e: Throwable,
    )

    fun exist(result: R): Boolean

    fun save(
        key: String,
        value: T,
        result: R,
    )

    fun convertToKey(value: T): String

    override suspend fun load(value: T): R? {
        return runCatching {
            val key = convertToKey(value)
            val result = resolve(key, value)
            if (exist(result)) {
                result
            } else {
                mutex.withLock(key) {
                    if (exist(result)) {
                        result
                    } else {
                        save(key, value, result)
                        if (exist(result)) {
                            result
                        } else {
                            null
                        }
                    }
                }
            }
        }.onFailure {
            loggerWarning(value, it)
        }.getOrNull()
    }
}
