package com.crosspaste.paste

import com.crosspaste.net.clientapi.ClientApiResult
import kotlinx.coroutines.flow.StateFlow

interface PasteSyncProcessManager<T> {

    val processMap: StateFlow<Map<Long, PasteSingleProcess>>

    suspend fun getProcess(
        key: T,
        taskNum: Int,
    ): PasteSingleProcess

    suspend fun cleanProcess(key: T)

    suspend fun runTask(
        pasteDataId: Long,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>>
}

interface PasteSingleProcess {

    val process: StateFlow<Float>

    fun success(index: Int)
}
