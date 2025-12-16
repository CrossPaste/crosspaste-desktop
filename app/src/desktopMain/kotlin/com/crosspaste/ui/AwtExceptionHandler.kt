package com.crosspaste.ui

import io.github.oshai.kotlinlogging.KotlinLogging

class AwtExceptionHandler {

    private val logger = KotlinLogging.logger {}

    @Suppress("unused")
    fun handle(throwable: Throwable) {
        val thread = Thread.currentThread()
        logger.error(throwable) {
            """
            AWT-EventQueue Crash
            Thread: ${thread.name}
            State: ${thread.state}
            """.trimIndent()
        }
    }
}
