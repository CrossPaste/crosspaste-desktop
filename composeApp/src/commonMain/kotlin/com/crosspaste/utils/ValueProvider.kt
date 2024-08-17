package com.crosspaste.utils

class ValueProvider<T> {
    private var lastSuccessfulValue: T? = null

    fun getValue(provider: () -> T): T? {
        return try {
            val newValue = provider()
            if (newValue != null) {
                lastSuccessfulValue = newValue
            }
            lastSuccessfulValue
        } catch (e: Exception) {
            lastSuccessfulValue
        }
    }
}
