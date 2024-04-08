package com.clipevery.task

import com.clipevery.utils.cpuDispatcher
import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TaskSemaphore(limit: Int) {
    private val semaphore = Semaphore(limit)

    suspend fun <T> withTaskLimit(
        tasks: List<suspend () -> T>,
        scope: CoroutineScope,
    ): List<T> =
        scope.async {
            semaphore.withPermit {
                tasks.map { task ->
                    async {
                        task()
                    }
                }.awaitAll()
            }
        }.await()

    companion object {

        private val cpuScope = CoroutineScope(cpuDispatcher + SupervisorJob())

        private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

        suspend fun <T> withCpuTaskLimit(
            limit: Int,
            tasks: List<suspend () -> T>,
        ): List<T> {
            val taskSemaphore = TaskSemaphore(limit)
            return taskSemaphore.withTaskLimit(tasks, cpuScope)
        }

        suspend fun <T> withIoTaskLimit(
            limit: Int,
            tasks: List<suspend () -> T>,
        ): List<T> {
            val taskSemaphore = TaskSemaphore(limit)
            return taskSemaphore.withTaskLimit(tasks, ioScope)
        }
    }
}
