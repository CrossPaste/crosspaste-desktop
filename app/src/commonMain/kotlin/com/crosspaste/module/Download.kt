package com.crosspaste.module

import okio.Path

data class DownloadTask(
    val id: String,
    val url: String,
    val fileName: String,
    val savePath: Path,
    val moduleId: String,
)

data class DownloadProgress(
    val taskId: String,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
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
        val filePath: Path,
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
    val fileStates: Map<String, DownloadState>,
)
