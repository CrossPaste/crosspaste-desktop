package com.crosspaste.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object GlobalCoroutineScope {

    val mainCoroutineDispatcher = CoroutineScope(SupervisorJob() + mainDispatcher)

    val ioCoroutineDispatcher = CoroutineScope(SupervisorJob() + ioDispatcher)

    val cpuCoroutineDispatcher = CoroutineScope(SupervisorJob() + cpuDispatcher)
}
