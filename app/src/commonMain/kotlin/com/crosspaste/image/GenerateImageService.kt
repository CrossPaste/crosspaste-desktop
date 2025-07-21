package com.crosspaste.image

import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path

class GenerateImageService {

    private val fileUtils = getFileUtils()

    private val generateMap: MutableMap<Path, MutableStateFlow<Boolean>> = mutableMapOf()

    private val mutex = Mutex()

    suspend fun awaitGeneration(
        path: Path,
        timeoutMillis: Long = 30_000,
    ) {
        if (fileUtils.existFile(path)) {
            return
        }

        val existingFlow =
            mutex.withLock {
                generateMap[path]
            }

        val stateFlow =
            existingFlow ?: mutex.withLock {
                generateMap.getOrPut(path) { MutableStateFlow(false) }
            }

        try {
            withTimeoutOrNull(timeoutMillis) {
                stateFlow.first { it }
            }
        } finally {
            mutex.withLock {
                generateMap.remove(path)
            }
        }
    }

    suspend fun markGenerationComplete(path: Path) {
        mutex.withLock {
            generateMap[path]?.value = true
        }
    }
}
