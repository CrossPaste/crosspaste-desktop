package com.clipevery.task

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.net.clientapi.PullClientApi
import com.clipevery.net.clientapi.SuccessResult
import com.clipevery.path.PathProvider
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.BaseExtraInfo
import com.clipevery.utils.FileUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
import com.clipevery.utils.buildUrl
import com.clipevery.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

class PullIconTaskExecutor(
    private val clipDao: ClipDao,
    private val pathProvider: PathProvider,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    companion object PullIconTaskExecutor {
        private val logger = KotlinLogging.logger {}

        private val fileUtils: FileUtils = getFileUtils()
    }

    override val taskType: Int = TaskType.PULL_ICON_TASK

    private val locks: MutableMap<String, Mutex> = ConcurrentHashMap<String, Mutex>()

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val baseExtraInfo: BaseExtraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class)

        clipDao.getClipData(clipTask.clipDataId!!)?.let { clipData ->
            clipData.source?.let { source ->
                locks.getOrPut(source) { Mutex() }.withLock {
                    try {
                        val appInstanceId = clipData.appInstanceId

                        val iconPath = pathProvider.resolve("$source.png", AppFileType.ICON)
                        if (!iconPath.exists()) {
                            syncManager.getSyncHandlers()[appInstanceId]?.let {
                                val port = it.syncRuntimeInfo.port
                                it.getConnectHostAddress()?.let { host ->
                                    return pullIcon(source, iconPath, host, port, baseExtraInfo)
                                } ?: run {
                                    return createFailureClipTaskResult(
                                        retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                                        startTime = clipTask.modifyTime,
                                        failMessage = "Failed to get connect host address",
                                        extraInfo = baseExtraInfo,
                                    )
                                }
                            } ?: run {
                                return createFailureClipTaskResult(
                                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                                    startTime = clipTask.modifyTime,
                                    failMessage = "Failed to get sync handler by $appInstanceId",
                                    extraInfo = baseExtraInfo,
                                )
                            }
                        }
                    } finally {
                        locks.remove(source)
                    }
                }
            }
        }
        return SuccessClipTaskResult()
    }

    private suspend fun pullIcon(
        source: String,
        iconPath: Path,
        host: String,
        port: Int,
        baseExtraInfo: BaseExtraInfo,
    ): ClipTaskResult {
        val toUrl: URLBuilder.(URLBuilder) -> Unit = { urlBuilder: URLBuilder ->
            buildUrl(urlBuilder, host, port, "pull", "icon", source)
        }

        val result = pullClientApi.pullIcon(toUrl)
        if (result is SuccessResult) {
            val byteReadChannel = result.getResult<ByteReadChannel>()
            fileUtils.writeFile(iconPath, byteReadChannel)

            logger.info { "Success to pull icon" }

            return SuccessClipTaskResult()
        } else {
            return createFailureClipTaskResult(
                retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                startTime = System.currentTimeMillis(),
                failMessage = "Failed to pull icon",
                extraInfo = baseExtraInfo,
            )
        }
    }
}
