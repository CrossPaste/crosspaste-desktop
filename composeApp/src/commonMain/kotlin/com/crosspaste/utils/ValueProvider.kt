package com.crosspaste.utils

class ValueProvider<T> {
    private var lastSuccessfulValue: T? = null

    fun getValue(provider: () -> T): T? {
        return try {
            val newValue = provider()
            lastSuccessfulValue = newValue
            newValue
        } catch (e: Exception) {
            lastSuccessfulValue
        }
    }
}
