package com.crosspaste.paste

import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.utils.createPlatformLock
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class DefaultPasteSyncProcessManager : PasteSyncProcessManager<Long> {

    private val logger = KotlinLogging.logger {}

    private val ioScope = namedScope(ioDispatcher, "DefaultPasteSyncProcessManager")

    private val semaphore = Semaphore(10)

    private val _processMap: MutableStateFlow<Map<Long, PasteSingleProcess>> =
        MutableStateFlow(mapOf())

    override val processMap: StateFlow<Map<Long, PasteSingleProcess>> = _processMap

    override suspend fun cleanProcess(key: Long) {
        withContext(mainDispatcher) {
            _processMap.update { it - key }
        }
    }

    override suspend fun getProcess(
        key: Long,
        taskNum: Int,
    ): PasteSingleProcess =
        withContext(mainDispatcher) {
            _processMap.value[key] ?: PasteSingleProcessImpl(taskNum).also { process ->
                _processMap.update { it + (key to process) }
            }
        }

    override suspend fun runTask(
        pasteDataId: Long,
        tasks: List<suspend () -> Pair<Int, ClientApiResult>>,
    ): List<Pair<Int, ClientApiResult>> {
        if (tasks.isEmpty()) return emptyList()
        val process = getProcess(pasteDataId, tasks.size)
        return ioScope
            .async {
                tasks
                    .map { task ->
                        async {
                            try {
                                semaphore.withPermit {
                                    withTimeout(60.seconds) {
                                        val result = task()
                                        if (result.second is SuccessResult) {
                                            process.success(result.first)
                                        }
                                        result
                                    }
                                }
                            } catch (e: TimeoutCancellationException) {
                                logger.warn(e) { "runTask timed out for pasteDataId=$pasteDataId" }
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }.await()
    }
}

class PasteSingleProcessImpl(
    private val taskNum: Int,
) : PasteSingleProcess {

    private val _process = MutableStateFlow(0.0f)

    override val process: StateFlow<Float> = _process

    private var successNum = 0

    private val tasks: MutableList<Boolean> = MutableList(taskNum) { false }

    private val platformLock = createPlatformLock()

    override fun success(index: Int) {
        platformLock.lock()
        try {
            if (index !in tasks.indices) {
                return
            }

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
