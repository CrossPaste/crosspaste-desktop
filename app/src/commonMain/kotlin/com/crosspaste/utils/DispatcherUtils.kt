package com.crosspaste.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

expect val ioDispatcher: CoroutineDispatcher

expect val mainDispatcher: CoroutineDispatcher

expect val cpuDispatcher: CoroutineDispatcher

object GlobalCoroutineScope {

    val mainCoroutineDispatcher = CoroutineScope(SupervisorJob() + mainDispatcher)

    val ioCoroutineDispatcher = CoroutineScope(SupervisorJob() + ioDispatcher)

    val cpuCoroutineDispatcher = CoroutineScope(SupervisorJob() + cpuDispatcher)
}
