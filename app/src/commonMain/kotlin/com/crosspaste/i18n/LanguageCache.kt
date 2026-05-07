package com.crosspaste.i18n

import io.ktor.util.collections.ConcurrentMap

/**
 * Thread-safe cache of [Copywriter] instances keyed by language code. Each platform's
 * [GlobalCopywriter] supplies its own [factory] (e.g. desktop loads from a JVM resource,
 * iOS reads from a bundled file, Android needs a `Context`) but the cache lifecycle
 * and `computeIfAbsent` semantics are identical, so they live in one place here.
 *
 * Lifetime is owned by the holding [GlobalCopywriter] instance — the singleton DI scope
 * makes that effectively process-wide in production.
 */
class LanguageCache(
    private val factory: (String) -> Copywriter,
) {
    private val map: ConcurrentMap<String, Copywriter> = ConcurrentMap()

    fun getOrCreate(language: String): Copywriter = map.computeIfAbsent(language) { factory(language) }
}
