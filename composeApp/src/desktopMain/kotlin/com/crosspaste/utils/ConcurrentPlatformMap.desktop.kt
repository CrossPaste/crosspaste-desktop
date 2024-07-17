package com.crosspaste.utils

import io.ktor.util.collections.*

actual fun <K, V> createConcurrentPlatformMap(): ConcurrentPlatformMap<K, V> {
    return ConcurrentPlatformMapImpl()
}

class ConcurrentPlatformMapImpl<K, V> : ConcurrentPlatformMap<K, V> {
    private val map = ConcurrentMap<K, V>()

    override fun get(key: K): V? {
        return map[key]
    }

    override fun put(
        key: K,
        value: V,
    ): V? {
        return map.put(key, value)
    }

    override fun computeIfAbsent(
        key: K,
        mappingFunction: (K) -> V,
    ): V {
        return map.computeIfAbsent(key) { mappingFunction(key) }
    }
}
