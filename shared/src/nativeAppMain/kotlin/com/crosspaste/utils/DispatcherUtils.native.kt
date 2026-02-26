package com.crosspaste.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual val mainDispatcher: CoroutineDispatcher = Dispatchers.Default

actual val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
