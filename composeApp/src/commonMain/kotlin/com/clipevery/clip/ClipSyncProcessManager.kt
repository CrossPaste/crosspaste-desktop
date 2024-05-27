package com.clipevery.clip

import kotlinx.coroutines.Job

interface ClipSyncProcessManager<T> {

    val processMap: MutableMap<T, ClipSingleProcess>

    fun getProcess(key: T): ClipSingleProcess
}

interface ClipSingleProcess {

    var process: Float

    val job: Job
}
