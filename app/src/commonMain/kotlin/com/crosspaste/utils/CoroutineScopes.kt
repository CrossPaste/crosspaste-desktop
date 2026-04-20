package com.crosspaste.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

private val logger = KotlinLogging.logger {}

// Prevents Kotlin/Native abort() from uncaught exceptions in sibling coroutines (e.g. Ktor HttpTimeout killer).
fun loggingExceptionHandler(name: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable) { "Unhandled exception in $name" }
    }

fun namedScope(
    dispatcher: CoroutineDispatcher,
    name: String,
    job: CompletableJob? = SupervisorJob(),
): CoroutineScope =
    job?.let {
        CoroutineScope(dispatcher + it + loggingExceptionHandler(name))
    } ?: CoroutineScope(dispatcher + loggingExceptionHandler(name))
