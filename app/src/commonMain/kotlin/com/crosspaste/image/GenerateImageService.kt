package com.crosspaste.image

import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import kotlin.coroutines.cancellation.CancellationException

class GenerateImageService {

    private val fileUtils = getFileUtils()

    private val generateMap: MutableMap<Path, MutableStateFlow<Boolean>> = mutableMapOf()

    private val mutex = Mutex()

    suspend fun awaitGeneration(
        path: Path,
        timeoutMillis: Long = 30_000,
    ): Boolean {
        if (fileUtils.existFile(path)) {
            return true
        }

        val stateFlow =
            mutex.withLock {
                generateMap.getOrPut(path) { MutableStateFlow(false) }
            }

        return try {
            val result =
                withTimeoutOrNull(timeoutMillis) {
                    stateFlow.first { it }
                }
            result ?: false
        } catch (e: CancellationException) {
            throw e
        } finally {
            mutex.withLock {
                val flow = generateMap[path]
                if (flow != null && flow.subscriptionCount.value == 0) {
                    generateMap.remove(path)
                }
            }
        }
    }

    suspend fun markGenerationComplete(path: Path) {
        mutex.withLock {
            generateMap[path]?.value = true
        }
    }
}
