package com.crosspaste.utils

import io.ktor.util.collections.ConcurrentMap

object Memoize {
    fun <T : Any, R> memoize(function: (T) -> R): (T) -> R {
        val cache = ConcurrentMap<T, R>()
        return {
            cache.getOrPut(it) { function(it) }
        }
    }
}
