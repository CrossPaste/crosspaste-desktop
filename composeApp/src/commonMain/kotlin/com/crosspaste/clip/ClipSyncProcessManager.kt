package com.crosspaste.clip

import com.crosspaste.net.clientapi.ClientApiResult
import org.mongodb.kbson.ObjectId

interface ClipSyncProcessManager<T> {

    val processMap: MutableMap<T, ClipSingleProcess>

    fun getProcess(key: ObjectId): ClipSingleProcess?

    fun getProcess(
        key: T,
        taskNum: Int,
    ): ClipSingleProcess

    fun cleanProcess(key: T)

    suspend fun runTask(
        clipDataId: ObjectId,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>>
}

interface ClipSingleProcess {

    var process: Float

    fun success(index: Int)
}
