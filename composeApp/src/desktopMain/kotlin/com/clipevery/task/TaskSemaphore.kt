package com.clipevery.task

import com.clipevery.utils.cpuDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TaskSemaphore(limit: Int) {
    private val semaphore = Semaphore(limit)
    private val coroutineScope = CoroutineScope(cpuDispatcher + SupervisorJob())

    suspend fun <T> withTaskLimit(tasks: List<suspend () -> T>): List<T> = coroutineScope.async {
        semaphore.withPermit {
            tasks.map { task ->
                async {
                    task()
                }
            }.awaitAll()
        }
    }.await()

    companion object {
        suspend fun <T> withTaskLimit(limit: Int, tasks: List<suspend () -> T>): List<T> {
            val taskSemaphore = TaskSemaphore(limit)
            return taskSemaphore.withTaskLimit(tasks)
        }
    }
}