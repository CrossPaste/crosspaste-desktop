package com.crosspaste.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

actual val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default

actual val unconfinedDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

object GlobalCoroutineScopeImpl : GlobalCoroutineScope {

    override val mainCoroutineDispatcher = CoroutineScope(mainDispatcher)

    override val ioCoroutineDispatcher = CoroutineScope(ioDispatcher)

    override val cpuCoroutineDispatcher = CoroutineScope(cpuDispatcher)
}
