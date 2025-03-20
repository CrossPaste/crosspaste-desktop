package com.crosspaste.image

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import kotlin.coroutines.resume

class GenerateImageService {

    private val fileUtils = getFileUtils()

    private val generateMap: MutableMap<Path, MutableStateFlow<Unit>> = mutableMapOf()

    private val mutex = Mutex()

    private val generateCheckScope = CoroutineScope(ioDispatcher + SupervisorJob())

    suspend fun awaitGeneration(
        path: Path,
        timeoutMillis: Long = 30 * 1000,
    ) {
        if (fileUtils.existFile(path)) {
            return
        }

        val stateFlow = getGenerateState(path)

        return suspendCancellableCoroutine { continuation ->
            val job =
                generateCheckScope.launch {
                    withTimeoutOrNull(timeoutMillis) {
                        stateFlow.collect { state ->
                            continuation.resume(Unit)
                            removeGenerateState(path)
                            cancel()
                        }
                    } ?: run {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                            removeGenerateState(path)
                        }
                    }
                }

            continuation.invokeOnCancellation {
                job.cancel()
            }
        }
    }

    suspend fun removeGenerateState(path: Path) {
        mutex.withLock(path) {
            generateMap.remove(path)
        }
    }

    suspend fun getGenerateState(path: Path): MutableStateFlow<Unit> {
        return mutex.withLock(path) {
            generateMap.getOrPut(path) {
                MutableStateFlow(Unit)
            }
        }
    }
}
