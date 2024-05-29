package com.clipevery.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class DesktopResolver(private val scope: CoroutineScope) {

    private val jobs = ConcurrentHashMap<String, Job>()
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    private fun getMutex(deviceId: String): Mutex = mutexes.computeIfAbsent(deviceId) { Mutex() }

    suspend fun resolveDevice(
        key: String,
        action: suspend () -> Unit,
    ) {
        val mutex = getMutex(key)
        mutex.withLock {
            jobs[key]?.cancel()
            jobs[key] =
                scope.launch {
                    try {
                        action()
                    } finally {
                        jobs.remove(key)
                    }
                }
        }
    }
}
