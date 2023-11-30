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