package com.crosspaste.paste

import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.createPlatformLock
import com.crosspaste.utils.ioDispatcher
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DefaultPasteSyncProcessManager : PasteSyncProcessManager<Long> {

    private val ioScope = CoroutineScope(ioDispatcher)

    private val semaphore = Semaphore(10)

    private val _processMap: MutableStateFlow<ConcurrentMap<Long, PasteSingleProcess>> =
        MutableStateFlow(ConcurrentMap())

    override val processMap: StateFlow<Map<Long, PasteSingleProcess>> = _processMap

    override suspend fun cleanProcess(key: Long) {
        mainCoroutineDispatcher.launch {
            _processMap.value.remove(key)
        }
    }

    override suspend fun getProcess(
        key: Long,
        taskNum: Int,
    ): PasteSingleProcess {
        return mainCoroutineDispatcher.async {
            _processMap.value.computeIfAbsent(key) {
                PasteSingleProcessImpl(taskNum)
            }
        }.await()
    }

    override suspend fun runTask(
        pasteDataId: Long,
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

    private val _process = MutableStateFlow<Float>(0.0f)

    override var process: StateFlow<Float> = _process

    private var successNum = 0

    private val tasks: MutableList<Boolean> = MutableList(taskNum) { false }

    private val platformLock = createPlatformLock()

    override fun success(index: Int) {
        platformLock.lock()
        try {
            if (!tasks[index]) {
                tasks[index] = true
                successNum += 1
                _process.value = successNum / taskNum.toFloat()
            }
        } finally {
            platformLock.unlock()
        }
    }
}
