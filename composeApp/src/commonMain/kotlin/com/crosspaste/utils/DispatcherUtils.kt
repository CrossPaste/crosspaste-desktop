package com.crosspaste.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

expect val ioDispatcher: CoroutineDispatcher

expect val mainDispatcher: CoroutineDispatcher

expect val cpuDispatcher: CoroutineDispatcher

object GlobalCoroutineScope {

    val mainCoroutineDispatcher = CoroutineScope(mainDispatcher)

    val ioCoroutineDispatcher = CoroutineScope(ioDispatcher)

    val cpuCoroutineDispatcher = CoroutineScope(cpuDispatcher)
}
