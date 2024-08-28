package com.crosspaste.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

object RetryUtils {

    private val logger: KLogger = KotlinLogging.logger {}

    fun <T> retry(
        maxRetries: Int,
        block: (Int) -> T,
    ): T? {
        repeat(maxRetries) { attempt ->
            try {
                val result = block(attempt)
                if (result != null) {
                    return result
                }
            } catch (e: Exception) {
                logger.error {
                    "Attempt ${attempt + 1}/$maxRetries failed with " +
                        "${e::class.simpleName}: ${e.message}"
                }
                if (attempt == maxRetries - 1) {
                    return null
                }
            }
        }
        return null
    }
}
