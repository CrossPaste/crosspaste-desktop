package com.crosspaste.utils

expect fun <K, V> createConcurrentPlatformMap(): ConcurrentPlatformMap<K, V>

interface ConcurrentPlatformMap<K, V> {

    fun get(key: K): V?

    fun put(
        key: K,
        value: V,
    ): V?

    fun computeIfAbsent(
        key: K,
        mappingFunction: (K) -> V,
    ): V
}
