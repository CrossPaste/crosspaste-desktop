package com.crosspaste.utils

object GlobalCoroutineScope {

    val mainCoroutineDispatcher = namedScope(mainDispatcher, "GlobalCoroutineScope.main")

    val ioCoroutineDispatcher = namedScope(ioDispatcher, "GlobalCoroutineScope.io")

    val cpuCoroutineDispatcher = namedScope(cpuDispatcher, "GlobalCoroutineScope.cpu")
}
