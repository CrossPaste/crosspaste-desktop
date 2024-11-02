package com.crosspaste.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default

val unconfinedDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

object GlobalCoroutineScope {

    val mainCoroutineDispatcher = CoroutineScope(mainDispatcher)

    val ioCoroutineDispatcher = CoroutineScope(ioDispatcher)

    val cpuCoroutineDispatcher = CoroutineScope(cpuDispatcher)
}
