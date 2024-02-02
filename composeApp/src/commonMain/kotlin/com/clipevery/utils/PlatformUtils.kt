package com.clipevery.utils

import kotlinx.coroutines.CoroutineDispatcher

expect val ioDispatcher: CoroutineDispatcher

expect val mainDispatcher: CoroutineDispatcher

expect val cpuDispatcher: CoroutineDispatcher

expect val unconfinedDispatcher: CoroutineDispatcher
