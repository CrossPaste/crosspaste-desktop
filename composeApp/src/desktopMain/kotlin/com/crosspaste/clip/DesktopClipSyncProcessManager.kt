package com.crosspaste.clip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.utils.ioDispatcher
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.mongodb.kbson.ObjectId

class DesktopClipSyncProcessManager : ClipSyncProcessManager<ObjectId> {

    private val ioScope = CoroutineScope(ioDispatcher)

    private val semaphore = Semaphore(10)

    override val processMap: MutableMap<ObjectId, ClipSingleProcess> = ConcurrentMap()

    override fun cleanProcess(key: ObjectId) {
        processMap.remove(key)
    }

    override fun getProcess(key: ObjectId): ClipSingleProcess? {
        return processMap[key]
    }

    override fun getProcess(
        key: ObjectId,
        taskNum: Int,
    ): ClipSingleProcess {
        return processMap.computeIfAbsent(key) {
            ClipSingleProcessImpl(taskNum)
        }
    }

    override suspend fun runTask(
        clipDataId: ObjectId,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>> {
        val process = getProcess(clipDataId, tasks.size)
        return ioScope.async {
            tasks.map { task ->
                async {
                    semaphore.withPermit {
                        val result = task()
                        if (result.second is SuccessResult) {
                            process.success(result.first)
                        }
                        result
                    }
                }
            }.awaitAll()
        }.await()
    }
}

class ClipSingleProcessImpl(private val taskNum: Int) : ClipSingleProcess {

    override var process: Float by mutableStateOf(0.0f)

    private var successNum = 0

    private val tasks: MutableList<Boolean> = MutableList(taskNum) { false }

    @Synchronized
    override fun success(index: Int) {
        if (!tasks[index]) {
            tasks[index] = true
            successNum += 1
            process = successNum / taskNum.toFloat()
        }
    }
}
