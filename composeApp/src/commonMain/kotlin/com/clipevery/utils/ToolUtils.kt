package com.clipevery.utils

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
    fun <R, T> memoize(vararg inputs: T, function: () -> R): () -> R {
        val cache = mutableMapOf<List<T>, R>()
        return {
            val key = inputs.toList()
            cache.getOrPut(key) { function() }
        }
    }

}
