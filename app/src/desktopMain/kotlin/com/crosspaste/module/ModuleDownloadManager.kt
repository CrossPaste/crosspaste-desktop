package com.crosspaste.module

import com.crosspaste.module.ModuleDownloadManager.Companion.dateUtils
import com.crosspaste.net.DownloadProgressListener
import com.crosspaste.net.ResourcesClient
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.ktor.http.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ModuleDownloadManager(
    private val moduleManager: ModuleManager,
    private val resourcesClient: ResourcesClient,
    private val downloadScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) {

    companion object Companion {

        val dateUtils = getDateUtils()

        val fileUtils = getFileUtils()
    }

    private val moduleDownloadStates = ConcurrentMap<String, MutableStateFlow<ModuleDownloadState>>()

    private val downloadTasks = ConcurrentMap<String, DownloadTask>()

    private val downloadJobs = ConcurrentMap<String, Job>()

    fun downloadFile(task: DownloadTask) {
        downloadTasks[task.id] = task

        updateDownloadState(
            task.id,
            DownloadState.Downloading(
                DownloadProgress(
                    taskId = task.id,
                    fileName = task.fileName,
                    downloadedBytes = 0,
                    totalBytes = null,
                    progress = 0f,
                    speed = 0L,
                    remainingTime = 0L,
                ),
            ),
        )

        val tracker =
            DownloadTracker(task) { state ->
                updateDownloadState(task.id, state)
            }

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
                                if (tracker.isCancelled || !downloadJobs.containsKey(task.id)) {
                                    return
                                }

                                tracker.updateProgress(bytesRead, contentLength)
                            }

                            override fun onSuccess() {
                                val completedState =
                                    DownloadState.Completed(
                                        task.id,
                                        filePath,
                                    )
                                updateDownloadState(task.id, completedState)
                            }

                            override fun onFailure(
                                httpStatusCode: HttpStatusCode,
                                throwable: Throwable?,
                            ) {
                                val error = throwable ?: Exception("HTTP Error: ${httpStatusCode.value}")
                                if (throwable is CancellationException) {
                                    tracker.isCancelled = true
                                    val cancelledState = DownloadState.Cancelled(task.id)
                                    updateDownloadState(task.id, cancelledState)
                                } else {
                                    val failedState = DownloadState.Failed(task.id, error)
                                    updateDownloadState(task.id, failedState)
                                }
                            }
                        }

                    resourcesClient.download(task.url, filePath, progressListener)
                } catch (e: Exception) {
                    if (tracker.isCancelled) {
                        val cancelledState = DownloadState.Cancelled(task.id)
                        updateDownloadState(task.id, cancelledState)
                    } else {
                        val failedState = DownloadState.Failed(task.id, e)
                        updateDownloadState(task.id, failedState)
                    }
                } finally {
                    downloadJobs.remove(task.id)
                }
            }

        downloadJobs[task.id] = job
    }

    fun cancelDownload(taskId: String) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)

        val cancelledState = DownloadState.Cancelled(taskId)
        updateDownloadState(taskId, cancelledState)
    }

    fun removeDownload(
        moduleId: String,
        taskId: String,
    ) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        val idleState = DownloadState.Idle(taskId)
        updateDownloadState(moduleId, taskId, idleState)
        moduleManager.getModuleById(moduleId)?.let { module ->
            module.getFilePath(taskId)?.let { filePath ->
                fileUtils.deleteFile(filePath)
            }
        }
    }

    fun getModuleDownloadState(moduleId: String): StateFlow<ModuleDownloadState> =
        moduleDownloadStates.getOrPut(moduleId) {
            moduleManager.getModuleById(moduleId)?.let { module ->
                MutableStateFlow(module.getModuleInitDownloadState())
            } ?: run {
                MutableStateFlow(
                    ModuleDownloadState(
                        moduleId = moduleId,
                        totalFiles = 0,
                        completedFiles = 0,
                        fileStates = mapOf(),
                    ),
                )
            }
        }

    fun updateDownloadState(
        taskId: String,
        state: DownloadState,
    ) {
        downloadTasks[taskId]?.moduleId?.let { moduleId ->
            updateDownloadState(moduleId, taskId, state)
        }
    }

    fun updateDownloadState(
        moduleId: String,
        taskId: String,
        state: DownloadState,
    ) {
        moduleDownloadStates[moduleId]?.let { moduleStateFlow ->
            val currentModuleState = moduleStateFlow.value
            val updatedFileStates = currentModuleState.fileStates + (taskId to state)
            val completedFiles = updatedFileStates.values.count { it is DownloadState.Completed }

            moduleStateFlow.value =
                currentModuleState.copy(
                    completedFiles = completedFiles,
                    fileStates = updatedFileStates,
                )
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
