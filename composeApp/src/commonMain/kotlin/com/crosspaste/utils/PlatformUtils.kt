package com.crosspaste.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

expect val ioDispatcher: CoroutineDispatcher

expect val mainDispatcher: CoroutineDispatcher

expect val cpuDispatcher: CoroutineDispatcher

expect val unconfinedDispatcher: CoroutineDispatcher

interface GlobalCoroutineScope {

    val mainCoroutineDispatcher: CoroutineScope

    val ioCoroutineDispatcher: CoroutineScope

    val cpuCoroutineDispatcher: CoroutineScope
}
