package com.crosspaste.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

actual fun getRetryUtils(): RetryUtils {
    return DesktopRetryUtils
}

object DesktopRetryUtils : RetryUtils {

    private val logger: KLogger = KotlinLogging.logger {}

    override fun <T> retry(
        maxRetries: Int,
        block: () -> T,
    ): T? {
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
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
