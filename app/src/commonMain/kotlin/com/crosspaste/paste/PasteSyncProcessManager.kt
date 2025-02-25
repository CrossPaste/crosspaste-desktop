package com.crosspaste.paste

import com.crosspaste.net.clientapi.ClientApiResult

interface PasteSyncProcessManager<T> {

    val processMap: MutableMap<T, PasteSingleProcess>

    fun getProcess(key: Long): PasteSingleProcess?

    fun getProcess(
        key: T,
        taskNum: Int,
    ): PasteSingleProcess

    fun cleanProcess(key: T)

    suspend fun runTask(
        pasteDataId: Long,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>>
}

interface PasteSingleProcess {

    var process: Float

    fun success(index: Int)
}
