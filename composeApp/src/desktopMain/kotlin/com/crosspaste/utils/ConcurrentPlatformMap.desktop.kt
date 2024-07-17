package com.crosspaste.utils

import io.ktor.util.collections.*

actual fun <K, V> createConcurrentPlatformMap(): ConcurrentPlatformMap<K, V> {
    return ConcurrentPlatformMap()
}

class ConcurrentPlatformMap<K, V> {
    private val map = ConcurrentMap<K, V>()

    fun get(key: K): V? {
        return map[key]
    }

    fun put(key: K, value: V): V? {
        return map.put(key, value)
    }

    fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V {
        return map.computeIfAbsent(key) { mappingFunction(key) }
    }
}