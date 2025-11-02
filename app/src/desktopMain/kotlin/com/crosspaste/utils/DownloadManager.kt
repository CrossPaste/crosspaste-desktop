package com.crosspaste.utils

import com.crosspaste.net.DownloadProgressListener
import com.crosspaste.net.ResourcesClient
import com.crosspaste.utils.DownloadManager.Companion.dateUtils
import io.ktor.http.*
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path

data class DownloadTask(
    val id: String,
    val url: String,
    val fileName: String,
    val savePath: Path,
    val moduleId: String? = null,
)

data class DownloadProgress(
    val taskId: String,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val speed: Long = 0L,
    val remainingTime: Long = 0L,
)

sealed class DownloadState {
    data class Idle(
        val taskId: String,
    ) : DownloadState()

    data class Downloading(
        val progress: DownloadProgress,
    ) : DownloadState()

    data class Completed(
        val taskId: String,
        val filePath: String,
    ) : DownloadState()

    data class Failed(
        val taskId: String,
        val error: Throwable,
    ) : DownloadState()

    data class Cancelled(
        val taskId: String,
    ) : DownloadState()
}

data class ModuleDownloadState(
    val moduleId: String,
    val totalFiles: Int,
    val completedFiles: Int,
    val overallProgress: Float,
    val fileStates: Map<String, DownloadState>,
)

class DownloadManager(
    private val resourcesClient: ResourcesClient,
    private val coroutineScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) {

    companion object {

        val dateUtils = getDateUtils()

        val fileUtils = getFileUtils()
    }

    private val downloadStates = MutableStateFlow<Map<String, DownloadState>>(mapOf())

    private val moduleDownloadStates = ConcurrentMap<String, MutableStateFlow<ModuleDownloadState>>()

    private val downloadTasks = ConcurrentMap<String, DownloadTask>()

    private val downloadJobs = ConcurrentMap<String, Job>()

    fun downloadFile(task: DownloadTask): Flow<DownloadState> =
        flow {
            downloadTasks[task.id] = task

            emit(DownloadState.Idle(task.id))
            updateDownloadState(task.id, DownloadState.Idle(task.id))

            val tracker =
                DownloadTracker(task) { state ->
                    updateDownloadState(task.id, state)
                }

            val downloadScope = CoroutineScope(coroutineScope.coroutineContext + Job())

            val job =
                downloadScope.launch {
                    try {
                        fileUtils.createDir(task.savePath)
                        val filePath = task.savePath.resolve(task.fileName)

                        val progressListener =
                            object : DownloadProgressListener {
                                override fun onProgress(
                                    bytesRead: Long,
                                    contentLength: Long?,
                                ) {
                                    if (!downloadJobs.containsKey(task.id)) {
                                        tracker.isCancelled = true
                                        downloadScope.cancel()
                                        return
                                    }

                                    tracker.updateProgress(bytesRead, contentLength)
                                }

                                override fun onSuccess() {
                                    val completedState =
                                        DownloadState.Completed(
                                            task.id,
                                            filePath.toFile().absolutePath,
                                        )
                                    updateDownloadState(task.id, completedState)
                                }

                                override fun onFailure(
                                    httpStatusCode: HttpStatusCode,
                                    throwable: Throwable?,
                                ) {
                                    val error = throwable ?: Exception("HTTP Error: ${httpStatusCode.value}")
                                    val failedState = DownloadState.Failed(task.id, error)
                                    updateDownloadState(task.id, failedState)
                                }
                            }

                        resourcesClient.download(task.url, filePath, progressListener)
                    } catch (e: Exception) {
                        if (tracker.isCancelled) {
                            val cancelledState = DownloadState.Cancelled(task.id)
                            updateDownloadState(task.id, cancelledState)

                            val filePath = task.savePath.resolve(task.fileName)
                            if (fileUtils.existFile(filePath)) {
                                fileUtils.deleteFile(filePath)
                            }
                        } else {
                            val failedState = DownloadState.Failed(task.id, e)
                            updateDownloadState(task.id, failedState)
                        }
                    } finally {
                        downloadJobs.remove(task.id)
                    }
                }

            downloadJobs[task.id] = job

            downloadStates
                .map { it[task.id] }
                .filterNotNull()
                .collect { state ->
                    emit(state)
                }
        }

    fun downloadModule(
        moduleId: String,
        tasks: List<DownloadTask>,
    ): Flow<ModuleDownloadState> =
        flow {
            val initialState =
                ModuleDownloadState(
                    moduleId = moduleId,
                    totalFiles = tasks.size,
                    completedFiles = 0,
                    overallProgress = 0f,
                    fileStates = emptyMap(),
                )

            val moduleStateFlow = MutableStateFlow(initialState)
            moduleDownloadStates[moduleId] = moduleStateFlow

            tasks.forEach { task ->
                val taskWithModule = task.copy(moduleId = moduleId)
                coroutineScope.launch {
                    downloadFile(taskWithModule).collect {
                    }
                }
            }

            moduleStateFlow.collect { state ->
                emit(state)
            }
        }

    fun cancelDownload(taskId: String) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)

        val cancelledState = DownloadState.Cancelled(taskId)
        updateDownloadState(taskId, cancelledState)
    }

    fun cancelModule(moduleId: String) {
        moduleDownloadStates[moduleId]?.value?.fileStates?.keys?.forEach { taskId ->
            cancelDownload(taskId)
        }
        moduleDownloadStates.remove(moduleId)
    }

    fun getDownloadState(taskId: String): DownloadState? = downloadStates.value[taskId]

    fun getModuleDownloadState(moduleId: String): Flow<ModuleDownloadState>? = moduleDownloadStates[moduleId]

    fun getAllDownloadStates(): Flow<Map<String, DownloadState>> = downloadStates

    private fun calculateOverallProgress(states: List<DownloadState>): Float {
        if (states.isEmpty()) return 0f

        val progresses =
            states.map { state ->
                when (state) {
                    is DownloadState.Downloading -> state.progress.progress
                    is DownloadState.Completed -> 1f
                    else -> 0f
                }
            }

        return progresses.average().toFloat()
    }

    private fun updateDownloadState(
        taskId: String,
        state: DownloadState,
    ) {
        downloadStates.value += (taskId to state)

        downloadTasks[taskId]?.moduleId?.let { moduleId ->
            moduleDownloadStates[moduleId]?.let { moduleStateFlow ->
                val currentModuleState = moduleStateFlow.value
                val updatedFileStates = currentModuleState.fileStates + (taskId to state)
                val completedFiles = updatedFileStates.values.count { it is DownloadState.Completed }
                val overallProgress = calculateOverallProgress(updatedFileStates.values.toList())

                moduleStateFlow.value =
                    currentModuleState.copy(
                        completedFiles = completedFiles,
                        overallProgress = overallProgress,
                        fileStates = updatedFileStates,
                    )
            }
        }
    }
}

private class DownloadTracker(
    val task: DownloadTask,
    val onStateUpdate: (DownloadState) -> Unit,
) {
    var lastUpdateTime = dateUtils.nowEpochMilliseconds()
    var lastDownloadedBytes = 0L
    var totalBytes = 0L
    var isCancelled = false

    fun updateProgress(
        downloadedBytes: Long,
        contentLength: Long?,
    ) {
        if (contentLength != null) {
            totalBytes = contentLength
        }

        val currentTime = dateUtils.nowEpochMilliseconds()
        val timeDiff = currentTime - lastUpdateTime

        if (timeDiff >= 500 || downloadedBytes == totalBytes) {
            val speed =
                calculateSpeed(
                    downloadedBytes - lastDownloadedBytes,
                    timeDiff,
                )
            val remainingTime =
                calculateRemainingTime(
                    totalBytes - downloadedBytes,
                    speed,
                )

            val progress =
                DownloadProgress(
                    taskId = task.id,
                    fileName = task.fileName,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f,
                    speed = speed,
                    remainingTime = remainingTime,
                )

            val state = DownloadState.Downloading(progress)
            onStateUpdate(state)

            lastUpdateTime = currentTime
            lastDownloadedBytes = downloadedBytes
        }
    }

    private fun calculateSpeed(
        bytes: Long,
        timeMillis: Long,
    ): Long = if (timeMillis > 0) (bytes * 1000 / timeMillis) else 0L

    private fun calculateRemainingTime(
        remainingBytes: Long,
        speed: Long,
    ): Long = if (speed > 0) remainingBytes / speed else 0L
}
