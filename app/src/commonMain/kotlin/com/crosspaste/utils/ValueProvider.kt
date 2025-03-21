package com.crosspaste.utils

import io.github.oshai.kotlinlogging.KotlinLogging

class ValueProvider<T> {

    private val logger = KotlinLogging.logger {}

    private val lock: PlatformLock = createPlatformLock()

    private var lastSuccessfulValue: T? = null

    fun getValue(provider: () -> T): T? {
        return runCatching {
            val newValue = provider()
            lock.withLock {
                if (newValue != null) {
                    lastSuccessfulValue = newValue
                }
                lastSuccessfulValue
            }
        }.getOrElse { e ->
            logger.warn(e) { "Error occurred while getting new value" }
            lastSuccessfulValue
        }
    }

    fun clear() {
        lock.withLock {
            lastSuccessfulValue = null
        }
    }
}
