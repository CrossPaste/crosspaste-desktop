package com.crosspaste.paste

import com.crosspaste.net.clientapi.ClientApiResult
import org.mongodb.kbson.ObjectId

interface PasteSyncProcessManager<T> {

    val processMap: MutableMap<T, PasteSingleProcess>

    fun getProcess(key: ObjectId): PasteSingleProcess?

    fun getProcess(
        key: T,
        taskNum: Int,
    ): PasteSingleProcess

    fun cleanProcess(key: T)

    suspend fun runTask(
        pasteDataId: ObjectId,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>>
}

interface PasteSingleProcess {

    var process: Float

    fun success(index: Int)
}
