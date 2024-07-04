package com.crosspaste.paste

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

class DesktopPasteSyncProcessManager : PasteSyncProcessManager<ObjectId> {

    private val ioScope = CoroutineScope(ioDispatcher)

    private val semaphore = Semaphore(10)

    override val processMap: MutableMap<ObjectId, PasteSingleProcess> = ConcurrentMap()

    override fun cleanProcess(key: ObjectId) {
        processMap.remove(key)
    }

    override fun getProcess(key: ObjectId): PasteSingleProcess? {
        return processMap[key]
    }

    override fun getProcess(
        key: ObjectId,
        taskNum: Int,
    ): PasteSingleProcess {
        return processMap.computeIfAbsent(key) {
            PasteSingleProcessImpl(taskNum)
        }
    }

    override suspend fun runTask(
        pasteDataId: ObjectId,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>> {
        val process = getProcess(pasteDataId, tasks.size)
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

class PasteSingleProcessImpl(private val taskNum: Int) : PasteSingleProcess {

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
