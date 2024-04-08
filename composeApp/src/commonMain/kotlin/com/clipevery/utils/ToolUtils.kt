package com.clipevery.utils

import io.github.oshai.kotlinlogging.KotlinLogging

class OnceFunction<T>(private val function: () -> T) {
    private var hasRun = false
    private var result: T? = null

    fun run(): T {
        if (!hasRun) {
            result = function()
            hasRun = true
        }
        return result!!
    }
}

object Memoize {
    fun <R, T> memoize(
        vararg inputs: T,
        function: () -> R,
    ): () -> R {
        val cache = mutableMapOf<List<T>, R>()
        return {
            val key = inputs.toList()
            cache.getOrPut(key) { function() }
        }
    }
}

object Retry {

    val logger = KotlinLogging.logger {}

    fun <T> retry(
        maxAttempts: Int,
        action: () -> T,
        cleanUp: () -> Unit = {},
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                logger.warn { "Attempt $attempt failed, retrying..." }
                cleanUp()
            }
        }
        throw lastException ?: IllegalStateException("Unknown retry failure.")
    }
}
